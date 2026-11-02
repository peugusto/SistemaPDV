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
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Consultas dos dashboards. Todas filtram pedido.data_pedido por faixa (>= de AND < ate),
 * sem DATE() na coluna, para o MySQL usar o índice idx_pedido_data (V9).
 */
public class RelatorioDaoJdbc implements RelatorioDao {

    private final Supplier<Connection> conexoes;

    public RelatorioDaoJdbc() { this(DB::getConnection); }

    public RelatorioDaoJdbc(Supplier<Connection> conexoes) {
        this.conexoes = Objects.requireNonNull(conexoes);
    }

    /** Pedido ainda aberto e venda cancelada não contam. */
    private static final String VENDA_VALIDA = " "; // A view já filtra cancelamentos e inclui devoluções na data correta.

    @Override
    public ResumoVendas resumo(LocalDateTime de, LocalDateTime ate) {
        ResumoVendas r = new ResumoVendas();
        try (Connection conn = conexoes.get()) {
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COALESCE(SUM(CASE WHEN p.sinal=1 THEN 1 ELSE 0 END),0), COALESCE(SUM(p.valor_total), 0), COALESCE(SUM(p.desconto), 0) FROM movimento_venda_relatorio p " +
                    "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA)) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    r.setQuantidadeVendas(rs.getInt(1));
                    r.setFaturamento(rs.getBigDecimal(2));
                    r.setDescontos(rs.getBigDecimal(3));
                }
            }
            // O desconto pertence ao pedido: rateia em centavos antes de somar o período.
            r.setDescontoComCusto(Money.ZERO);
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT p.id_pedido, p.desconto, COALESCE(SUM(p.sinal * i.quantidade * i.preco_unitario), 0) AS receita, " +
                    "COALESCE(SUM(CASE WHEN i.custo_unitario IS NOT NULL THEN p.sinal * i.quantidade * i.preco_unitario END), 0) AS receita_com_custo, " +
                    "COALESCE(SUM(p.sinal * i.quantidade * i.custo_unitario), 0) AS custo " +
                    "FROM itempedido i JOIN movimento_venda_relatorio p ON ABS(p.id_pedido) = i.id_pedido " +
                    "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA +
                    "GROUP BY p.id_pedido, p.desconto")) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        r.adicionarCustosDoPedido(rs.getBigDecimal("receita"), rs.getBigDecimal("receita_com_custo"),
                                rs.getBigDecimal("custo"), rs.getBigDecimal("desconto"));
                    }
                }
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COUNT(*) FROM pedido p WHERE p.data_pedido >= ? AND p.data_pedido < ? " +
                    "AND p.status = 'CANCELADO' AND p.id_pedido_troca IS NULL AND NOT EXISTS(SELECT 1 FROM devolucao_venda d WHERE d.id_pedido=p.id_pedido)")) {
                periodo(st, 1, de, ate);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    r.setCanceladas(rs.getInt(1));
                }
            }

            // Pago na hora da venda, por forma (o fiado é o que faltou pagar)
            BigDecimal pagoNaHora = Money.ZERO;
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT pg.metodo_pagamento, SUM(p.sinal * pg.valor_pago) FROM pagamento_caixa pg " +
                    "INNER JOIN movimento_venda_relatorio p ON ABS(p.id_pedido) = pg.id_pedido " +
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
            r.setFiadoLancado(r.getFaturamento().subtract(pagoNaHora));
            r.setPorForma(PaymentMethod.FIADO, r.getFiadoLancado());

            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COALESCE(SUM(valor_pago), 0) FROM pagamento_caixa " +
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
        String sql = "SELECT DATE(p.data_pedido) AS dia, SUM(CASE WHEN p.sinal=1 THEN 1 ELSE 0 END), SUM(p.valor_total) FROM movimento_venda_relatorio p " +
                "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA +
                "GROUP BY dia ORDER BY dia";
        try (Connection conn = conexoes.get();
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
        String sql = "SELECT HOUR(p.data_pedido) AS hora, SUM(CASE WHEN p.sinal=1 THEN 1 ELSE 0 END), SUM(p.valor_total) FROM movimento_venda_relatorio p " +
                "WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA +
                "GROUP BY hora ORDER BY hora";
        try (Connection conn = conexoes.get();
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
                "       COUNT(DISTINCT CASE WHEN v.id_pedido>0 THEN v.id_pedido END) AS vendas, " +
                "       COALESCE(SUM(v.quantidade * v.preco_unitario), 0) AS valor, " +
                "       MAX(CASE WHEN v.id_pedido>0 THEN v.data_pedido END) AS ultima, " +
                "       SUM(v.quantidade * v.custo_unitario) AS custo, " +
                "       COALESCE(SUM(CASE WHEN v.custo_unitario IS NOT NULL THEN v.quantidade * v.preco_unitario END), 0) AS valor_com_custo " +
                "FROM produto pr " +
                "LEFT JOIN (SELECT ip.id_produto, p.id_pedido, p.sinal * ip.quantidade AS quantidade, ip.preco_unitario, ip.custo_unitario, p.data_pedido " +
                "           FROM itempedido ip INNER JOIN movimento_venda_relatorio p ON ABS(p.id_pedido) = ip.id_pedido " +
                "           WHERE p.data_pedido >= ? AND p.data_pedido < ?" + VENDA_VALIDA + ") v " +
                "       ON v.id_produto = pr.id_produto " +
                "GROUP BY pr.id_produto";
        ProductDaoJdbc produtos = new ProductDaoJdbc();
        List<VendaProduto> lista = new ArrayList<>();
        try (Connection conn = conexoes.get();
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
