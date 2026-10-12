package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.RelatorioDao;
import com.casarural.sistemapdv.model.entities.PontoVenda;
import com.casarural.sistemapdv.model.entities.ResumoVendas;
import com.casarural.sistemapdv.model.entities.VendaProduto;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas dos dashboards. Todas filtram pedido.data_pedido por faixa (>= de AND < ate),
 * sem DATE() na coluna, para o MySQL usar o índice idx_pedido_data (V9).
 */
public class RelatorioDaoJdbc implements RelatorioDao {

    /** Pedido ainda aberto e venda cancelada não contam. */
    private static final String VENDA_VALIDA = " AND p.status NOT IN ('EM_ANDAMENTO', 'CANCELADO') ";

    @Override
    public ResumoVendas resumo(LocalDateTime de, LocalDateTime ate) {
        ResumoVendas r = new ResumoVendas();
        try (Connection conn = DB.getConnection()) {
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(p.valor_total), 0), COALESCE(SUM(p.desconto), 0) FROM pedido p " +
                    "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA)) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    r.setQuantidadeVendas(rs.getInt(1));
                    r.setFaturamento(rs.getBigDecimal(2));
                    r.setDescontos(rs.getBigDecimal(3));
                }
            }
            // Itens: receita, parte com custo e custo (V14)
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COALESCE(SUM(i.quantidade * i.preco_unitario), 0), " +
                    "COALESCE(SUM(CASE WHEN i.custo_unitario IS NOT NULL THEN i.quantidade * i.preco_unitario END), 0), " +
                    "COALESCE(SUM(i.quantidade * i.custo_unitario), 0) " +
                    "FROM itempedido i JOIN pedido p ON p.id_pedido = i.id_pedido " +
                    "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA)) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    r.setReceitaItens(rs.getBigDecimal(1));
                    r.setReceitaComCusto(rs.getBigDecimal(2));
                    r.setCustoItens(rs.getBigDecimal(3));
                }
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COUNT(*) FROM pedido p WHERE p.data_pedido >= ? AND p.data_pedido < ? " +
                    "AND p.status = 'CANCELADO' AND p.id_pedido_troca IS NULL")) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    r.setCanceladas(rs.getInt(1));
                }
            }

            // Pago na hora da venda, por forma (o fiado é o que faltou pagar)
            BigDecimal pagoNaHora = Money.ZERO;
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT pg.metodo_pagamento, SUM(pg.valor_pago) FROM pagamento pg " +
                    "INNER JOIN pedido p ON p.id_pedido = pg.id_pedido " +
                    "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA +
                    "AND pg.origem = 'VENDA' AND pg.metodo_pagamento <> 'FIADO' " +
                    "GROUP BY pg.metodo_pagamento")) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal valor = Money.of(rs.getBigDecimal(2));
                        Optional<PaymentMethod> forma = forma(rs.getString(1));
                        if (forma.isPresent()) {
                            r.setPorForma(forma.get(), valor);
                        } else {
                            // Ex.: "PAGO" nos primeiros pedidos de teste. Foi pago na hora; só não se sabe como.
                            r.setOutrasFormas(r.getOutrasFormas().add(valor));
                        }
                        pagoNaHora = pagoNaHora.add(valor);
                    }
                }
            }
            r.setFiadoLancado(Money.max(Money.ZERO, r.getFaturamento().subtract(pagoNaHora)));
            r.setPorForma(PaymentMethod.FIADO, r.getFiadoLancado());

            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COALESCE(SUM(valor_pago), 0) FROM pagamento " +
                    "WHERE origem = 'RECEBIMENTO_FIADO' AND data_pagamento >= ? AND data_pagamento < ?")) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    r.setRecebidoDeFiado(rs.getBigDecimal(1));
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return r;
    }

    @Override
    public Map<LocalDate, PontoVenda> vendasPorDia(LocalDateTime de, LocalDateTime ate) {
        Map<LocalDate, PontoVenda> mapa = new LinkedHashMap<>();
        String sql = "SELECT DATE(p.data_pedido) AS dia, COUNT(*), SUM(p.valor_total) FROM pedido p " +
                "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA +
                "GROUP BY dia ORDER BY dia";
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            periodo(st, 1, de, ate);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    LocalDate dia = rs.getDate(1).toLocalDate();
                    mapa.put(dia, new PontoVenda(dia.toString(), rs.getInt(2), rs.getBigDecimal(3)));
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return mapa;
    }

    @Override
    public Map<Integer, PontoVenda> vendasPorHora(LocalDateTime de, LocalDateTime ate) {
        Map<Integer, PontoVenda> mapa = new LinkedHashMap<>();
        String sql = "SELECT HOUR(p.data_pedido) AS hora, COUNT(*), SUM(p.valor_total) FROM pedido p " +
                "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA +
                "GROUP BY hora ORDER BY hora";
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            periodo(st, 1, de, ate);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int hora = rs.getInt(1);
                    mapa.put(hora, new PontoVenda(hora + "h", rs.getInt(2), rs.getBigDecimal(3)));
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return mapa;
    }

    @Override
    public List<VendaProduto> vendasPorProduto(LocalDateTime de, LocalDateTime ate) {
        // LEFT JOIN: produto que não vendeu também aparece (com zero), para achar os "parados"
        String sql =
                "SELECT pr.*, " +
                "       COALESCE(SUM(v.quantidade), 0) AS qtd, " +
                "       COUNT(DISTINCT v.id_pedido) AS vendas, " +
                "       COALESCE(SUM(v.quantidade * v.preco_unitario), 0) AS valor, " +
                "       MAX(v.data_pedido) AS ultima, " +
                "       SUM(v.quantidade * v.custo_unitario) AS custo, " +
                "       COALESCE(SUM(CASE WHEN v.custo_unitario IS NOT NULL THEN v.quantidade * v.preco_unitario END), 0) AS valor_com_custo " +
                "FROM produto pr " +
                "LEFT JOIN (SELECT ip.id_produto, ip.id_pedido, ip.quantidade, ip.preco_unitario, ip.custo_unitario, p.data_pedido " +
                "           FROM itempedido ip INNER JOIN pedido p ON p.id_pedido = ip.id_pedido " +
                "           WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA + ") v " +
                "       ON v.id_produto = pr.id_produto " +
                "GROUP BY pr.id_produto";
        ProductDaoJdbc produtos = new ProductDaoJdbc();
        List<VendaProduto> lista = new ArrayList<>();
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            periodo(st, 1, de, ate);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Timestamp ultima = rs.getTimestamp("ultima");
                    VendaProduto v = new VendaProduto(produtos.instantiate(rs), rs.getInt("qtd"), rs.getInt("vendas"),
                            rs.getBigDecimal("valor"), ultima == null ? null : ultima.toLocalDateTime());
                    v.setCusto(rs.getBigDecimal("custo"), rs.getBigDecimal("valor_com_custo"));
                    lista.add(v);
                }
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
        return lista;
    }

    private static Optional<PaymentMethod> forma(String texto) {
        if (texto == null) return Optional.empty();
        try {
            return Optional.of(PaymentMethod.valueOf(texto));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void periodo(PreparedStatement st, int primeiro, LocalDateTime de, LocalDateTime ate) throws SQLException {
        st.setTimestamp(primeiro, Timestamp.valueOf(de));
        st.setTimestamp(primeiro + 1, Timestamp.valueOf(ate));
    }
}
