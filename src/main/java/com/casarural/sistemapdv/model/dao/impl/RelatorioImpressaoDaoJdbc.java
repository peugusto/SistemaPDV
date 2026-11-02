package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.RelatorioImpressaoDao;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.services.impressao.Comprovante;
import com.casarural.sistemapdv.services.impressao.ComprovanteHistorico;
import java.sql.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** Consultas de impressão: não usa os objetos antigos da tela nem executa escritas. */
public class RelatorioImpressaoDaoJdbc implements RelatorioImpressaoDao {
    private final Supplier<Connection> conexoes;
    private final Clock relogio;
    public RelatorioImpressaoDaoJdbc() { this(DB::getConnection, Clock.systemDefaultZone()); }
    RelatorioImpressaoDaoJdbc(Supplier<Connection> conexoes, Clock relogio) {
        this.conexoes = conexoes;
        this.relogio = relogio;
    }
    @FunctionalInterface private interface Consulta<T> { T ler(Connection conn) throws SQLException; }
    private <T> T consultar(Consulta<T> consulta) {
        try (Connection conn = conexoes.get()) {
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            try {
                T dados = consulta.ler(conn);
                conn.commit();
                return dados;
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException | RuntimeException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw new DbException("Não foi possível consultar os dados para impressão. Tente imprimir novamente.", e);
        }
    }

    @Override public ExtratoFiado extratoFiado(int idCliente) {
        if (idCliente <= 0) throw new IllegalArgumentException("Selecione um cliente para imprimir.");
        return consultar(conn -> {
            Customer cliente;
            try (PreparedStatement st = conn.prepareStatement("SELECT id_cliente, nome_cliente, telefone FROM cliente WHERE id_cliente = ?")) {
                st.setInt(1, idCliente);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) throw new DbException("Este cliente não foi encontrado. Atualize a lista.");
                    cliente = cliente(rs);
                }
            }
            Map<Integer, Order> compras = pedidos(conn, "p.id_cliente = ? AND p.status = 'FIADO'", idCliente);
            compras.values().removeIf(p -> !Money.isPositive(p.getFalta()));
            carregarItens(conn, compras, "p.id_cliente = ? AND p.status = 'FIADO'", idCliente);
            return new ExtratoFiado(cliente, LocalDateTime.now(relogio), new ArrayList<>(compras.values()));
        });
    }

    @Override public FechamentoCaixa fechamentoCaixa(int idCaixa) {
        if (idCaixa <= 0) throw new IllegalArgumentException("Selecione um caixa para imprimir.");
        return consultar(conn -> {
            CashRegister caixa;
            try (PreparedStatement st = conn.prepareStatement("SELECT * FROM caixa WHERE id_caixa = ?")) {
                st.setInt(1, idCaixa);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) throw new DbException("Este caixa não foi encontrado.");
                    caixa = caixa(rs);
                }
            }
            if (caixa.isAberto()) throw new DbException("Feche o caixa antes de imprimir o fechamento.");
            Map<Integer, Order> vendas = pedidos(conn, "p.id_caixa = ?", idCaixa);
            carregarItens(conn, vendas, "p.id_caixa = ?", idCaixa);
            // O comprovante de um caixa encerrado mostra a venda como estava no seu fechamento.
            try(PreparedStatement st=conn.prepareStatement("SELECT d.id_pedido,d.status_original FROM devolucao_venda d JOIN pedido p ON p.id_pedido=d.id_pedido WHERE p.id_caixa=? AND d.id_caixa<>?")){
                st.setInt(1,idCaixa);st.setInt(2,idCaixa);try(ResultSet rs=st.executeQuery()){while(rs.next()){
                    Order venda=vendas.get(rs.getInt("id_pedido"));if(venda!=null)venda.setStatus(OrderStatus.valueOf(rs.getString("status_original")));
                }}
            }
            CashSummary resumo = new CashSummary(caixa.getValorInicial());
            List<Payment> recebimentos = new ArrayList<>();
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT pg.*, c.id_cliente, c.nome_cliente, c.telefone FROM pagamento_caixa pg "
                    + "JOIN pedido p ON p.id_pedido = pg.id_pedido LEFT JOIN cliente c ON c.id_cliente = p.id_cliente "
                    + "WHERE pg.id_caixa = ? ORDER BY pg.data_pagamento, pg.id_pagamento")) {
                st.setInt(1, idCaixa);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        PaymentMethod forma = PaymentMethod.ler(rs.getString("metodo_pagamento"));
                        if (forma == null) throw new DbException("Há uma forma de pagamento antiga sem identificação. Confira o caixa antes de imprimir.");
                        PaymentOrigin origem = PaymentOrigin.valueOf(rs.getString("origem"));
                        resumo.addPagamento(origem, forma, rs.getBigDecimal("valor_pago"));
                        if (origem == PaymentOrigin.VENDA) {
                            Order venda = vendas.get(rs.getInt("id_pedido"));
                            if (venda != null) venda.getPagamentos().add(new Order.PaymentSlice(rs.getBigDecimal("valor_pago"), forma));
                        } else {
                            Order compra = new Order(); compra.setIdPedido(rs.getInt("id_pedido")); compra.setCostumer(cliente(rs));
                            Payment pg = new Payment(compra, rs.getBigDecimal("valor_pago"), data(rs, "data_pagamento"), forma);
                            pg.setIdPagamento(rs.getInt("id_pagamento")); pg.setOrigem(origem); pg.setIdCaixa(idCaixa);
                            recebimentos.add(pg);
                        }
                    }
                }
            }
            List<CashMovement> movimentos = new ArrayList<>();
            try (PreparedStatement st = conn.prepareStatement("SELECT * FROM movimento_caixa WHERE id_caixa = ? ORDER BY data_movimento, id_movimento")) {
                st.setInt(1, idCaixa);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        CashMovement m = new CashMovement(); m.setIdMovimento(rs.getInt("id_movimento")); m.setIdCaixa(idCaixa);
                        m.setTipo(CashMovementType.valueOf(rs.getString("tipo"))); m.setValor(rs.getBigDecimal("valor"));
                        m.setMotivo(rs.getString("motivo")); m.setDataMovimento(data(rs, "data_movimento"));
                        movimentos.add(m); resumo.addMovimento(m.getTipo(), m.getValor());
                    }
                }
            }
            DevolucaoVendaJdbc.compensar(conn,idCaixa,resumo);
            return new FechamentoCaixa(caixa, resumo, new ArrayList<>(vendas.values()), movimentos, recebimentos,DevolucaoVendaJdbc.carregar(conn,idCaixa));
        });
    }

    @Override public Comprovante vendaHistorica(int idVenda) {
        if(idVenda<=0)throw new IllegalArgumentException("Selecione uma venda.");
        return consultar(conn -> {
            Map<Integer,Order> compras=pedidos(conn,"p.id_pedido = ?",idVenda);
            Order venda=compras.get(idVenda);
            if(venda==null)throw new DbException("Venda não encontrada.");
            if(venda.getStatus()==OrderStatus.CANCELADO)throw new DbException("Esta venda foi cancelada ou trocada. Não é possível emitir um comprovante de venda válido.");
            carregarItens(conn,compras,"p.id_pedido = ?",idVenda);
            try(PreparedStatement st=conn.prepareStatement("SELECT metodo_pagamento,valor_pago FROM pagamento WHERE id_pedido=? AND origem='VENDA' ORDER BY id_pagamento")) {
                st.setInt(1,idVenda);try(ResultSet rs=st.executeQuery()) { while(rs.next()) {
                    PaymentMethod forma=PaymentMethod.ler(rs.getString("metodo_pagamento"));
                    if(forma==null)throw new DbException("Há uma forma de pagamento antiga sem identificação.");
                    venda.getPagamentos().add(new Order.PaymentSlice(rs.getBigDecimal("valor_pago"),forma));
                }}
            }
            return ComprovanteHistorico.venda(venda);
        });
    }

    @Override public Comprovante recebimentoHistorico(int idCliente,Integer idRecebimento,LocalDateTime data,PaymentMethod forma) {
        if(idCliente<=0 || (idRecebimento!=null && idRecebimento<=0) || data==null || forma==null || forma==PaymentMethod.FIADO)
            throw new IllegalArgumentException("Selecione um recebimento válido.");
        return consultar(conn -> {
            Customer c;
            try(PreparedStatement st=conn.prepareStatement("SELECT id_cliente,nome_cliente,telefone FROM cliente WHERE id_cliente=?")) {
                st.setInt(1,idCliente);try(ResultSet rs=st.executeQuery()) {if(!rs.next())throw new DbException("Cliente não encontrado.");c=cliente(rs);}
            }
            if(idRecebimento!=null)try(PreparedStatement st=conn.prepareStatement("SELECT 1 FROM pagamento_estornado WHERE id_recebimento=? LIMIT 1")) {
                st.setInt(1,idRecebimento);try(ResultSet rs=st.executeQuery()) {if(rs.next())throw new DbException("Este recebimento foi total ou parcialmente desfeito. Atualize o histórico.");}
            }
            String filtro=idRecebimento==null?"pg.id_recebimento IS NULL AND pg.data_pagamento=? AND pg.metodo_pagamento=?":"pg.id_recebimento=?";
            List<Integer> ids=new ArrayList<>();BigDecimal pago=Money.ZERO;
            try(PreparedStatement st=conn.prepareStatement("SELECT pg.id_pedido,pg.valor_pago,pg.data_pagamento,pg.metodo_pagamento FROM pagamento pg JOIN pedido p ON p.id_pedido=pg.id_pedido WHERE p.id_cliente=? AND pg.origem='RECEBIMENTO_FIADO' AND "+filtro+" ORDER BY pg.id_pagamento")) {
                st.setInt(1,idCliente);
                if(idRecebimento==null) {st.setTimestamp(2,Timestamp.valueOf(data));st.setString(3,forma.name());} else st.setInt(2,idRecebimento);
                try(ResultSet rs=st.executeQuery()) {while(rs.next()) {
                    if(!data.equals(data(rs,"data_pagamento")) || forma!=PaymentMethod.ler(rs.getString("metodo_pagamento")))
                        throw new DbException("O recebimento mudou. Atualize o histórico antes de imprimir.");
                    ids.add(rs.getInt("id_pedido"));pago=pago.add(Money.of(rs.getBigDecimal("valor_pago")));
                }}
            }
            if(ids.isEmpty())throw new DbException("Recebimento não encontrado ou já desfeito. Atualize o histórico.");
            List<Order> compras=new ArrayList<>();
            for(Integer id:new LinkedHashSet<>(ids)) {
                Map<Integer,Order> uma=pedidos(conn,"p.id_pedido = ?",id);carregarItens(conn,uma,"p.id_pedido = ?",id);compras.addAll(uma.values());
            }
            return ComprovanteHistorico.recebimento(c,data,forma,pago,compras,idRecebimento==null);
        });
    }

    private static Map<Integer, Order> pedidos(Connection conn, String filtro, int id) throws SQLException {
        String sql = "SELECT p.*, c.nome_cliente, c.telefone, "
                + "COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg WHERE pg.id_pedido = p.id_pedido AND pg.origem = 'VENDA' AND pg.metodo_pagamento <> 'FIADO'), 0) AS pago_na_venda, "
                + "COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg WHERE pg.id_pedido = p.id_pedido AND pg.origem = 'RECEBIMENTO_FIADO'), 0) AS recebido_depois "
                + "FROM pedido p LEFT JOIN cliente c ON c.id_cliente = p.id_cliente WHERE " + filtro + " ORDER BY p.data_pedido, p.id_pedido";
        Map<Integer, Order> pedidos = new LinkedHashMap<>();
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Order p = new Order(); p.setIdPedido(rs.getInt("id_pedido")); p.setDataPedido(data(rs, "data_pedido"));
                    p.setValorTotal(rs.getBigDecimal("valor_total")); p.setDesconto(rs.getBigDecimal("desconto"));
                    p.setStatus(OrderStatus.valueOf(rs.getString("status"))); p.setCostumer(cliente(rs));
                    p.setPagoNaVenda(rs.getBigDecimal("pago_na_venda")); p.setRecebidoDepois(rs.getBigDecimal("recebido_depois"));
                    p.setItemPedido(new ArrayList<>()); pedidos.put(p.getIdPedido(), p);
                }
            }
        }
        return pedidos;
    }
    private static void carregarItens(Connection conn, Map<Integer, Order> pedidos, String filtro, int id) throws SQLException {
        if (pedidos.isEmpty()) return;
        try (PreparedStatement st = conn.prepareStatement(
                "SELECT i.id_pedido, i.quantidade, i.preco_unitario, pr.nome_produto FROM itempedido i "
                + "JOIN pedido p ON p.id_pedido = i.id_pedido LEFT JOIN produto pr ON pr.id_produto = i.id_produto WHERE "
                + filtro + " ORDER BY p.data_pedido, p.id_pedido, i.id_item")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Order p = pedidos.get(rs.getInt("id_pedido"));
                    if (p == null) continue;
                    Product produto = new Product();
                    produto.setNomeProduto(rs.getString("nome_produto") == null ? "Produto indisponível" : rs.getString("nome_produto"));
                    p.getItemPedido().add(new OrderItem(produto, rs.getInt("quantidade"), rs.getBigDecimal("preco_unitario")));
                }
            }
        }
    }
    private static Customer cliente(ResultSet rs) throws SQLException {
        if (rs.getString("nome_cliente") == null) return null;
        Customer c = new Customer(); c.setIdCliente(rs.getInt("id_cliente")); c.setNomeCliente(rs.getString("nome_cliente")); c.setTelefone(rs.getString("telefone")); return c;
    }
    private static LocalDateTime data(ResultSet rs, String coluna) throws SQLException {
        Timestamp t = rs.getTimestamp(coluna); return t == null ? null : t.toLocalDateTime();
    }
    private static CashRegister caixa(ResultSet rs) throws SQLException {
        CashRegister c = new CashRegister(); c.setIdCaixa(rs.getInt("id_caixa"));
        c.setAbertoEm(data(rs,"aberto_em")); c.setFechadoEm(data(rs,"fechado_em")); c.setValorInicial(rs.getBigDecimal("valor_inicial"));
        c.setValorEsperado(rs.getBigDecimal("valor_esperado")); c.setValorContado(rs.getBigDecimal("valor_contado")); c.setObservacao(rs.getString("observacao")); return c;
    }
}
