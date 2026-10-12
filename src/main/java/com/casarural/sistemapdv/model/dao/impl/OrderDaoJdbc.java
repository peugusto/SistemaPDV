package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.SaleFilter;
import com.casarural.sistemapdv.model.entities.SaleSummary;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OrderDaoJdbc implements OrderDao {

    public static final String MENSAGEM_CAIXA_FECHADO =
            "O caixa está fechado. Abra o caixa para registrar vendas e recebimentos de fiado.";

    /**
     * Caixa aberto, lido dentro da mesma transação que grava os pagamentos.
     * Sem caixa aberto, lança SQLException: a transação inteira é desfeita.
     */
    private int idCaixaAberto(Connection conn) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement("SELECT id_caixa FROM caixa WHERE fechado_em IS NULL");
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException(MENSAGEM_CAIXA_FECHADO);
    }

    @Override
    public void insert(Order obj) {
        // Uma única conexão do início ao fim da transação: pedido + itens + estoque + pagamentos
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement st = conn.prepareStatement(
                    "INSERT INTO pedido (id_cliente, valor_total, status, desconto) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                int idCaixa = idCaixaAberto(conn);   // antes de gravar qualquer coisa

                // Troca: desfaz a venda antiga primeiro (o estoque dela volta e pode ser vendido de novo)
                if (obj.isTroca()) {
                    cancelarNaTransacao(conn, obj.getIdPedidoTrocado(), "Troca", TIPO_TROCA);
                }

                if (obj.getCostumer() != null) {
                    st.setInt(1, obj.getCostumer().getIdCliente());
                } else {
                    st.setNull(1, Types.INTEGER);
                }
                st.setBigDecimal(2, obj.getValorTotal());
                st.setString(3, obj.getStatus().toString());
                st.setBigDecimal(4, obj.getDesconto());

                int rowsAffected = st.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet rs = st.getGeneratedKeys()) {
                        if (rs.next()) {
                            int idPedido = rs.getInt(1);
                            obj.setIdPedido(idPedido);
                            inserirItensEBaixarEstoque(conn, obj.getItemPedido(), idPedido);
                            inserirPagamento(conn, obj, idPedido, idCaixa);
                            if (obj.isTroca()) {
                                try (PreparedStatement up = conn.prepareStatement(
                                        "UPDATE pedido SET id_pedido_troca = ? WHERE id_pedido = ?")) {
                                    up.setInt(1, idPedido);
                                    up.setInt(2, obj.getIdPedidoTrocado());
                                    up.executeUpdate();
                                }
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    private void inserirItensEBaixarEstoque(Connection conn, List<OrderItem> itens, int idPedido) throws SQLException {
        String sqlItem = "INSERT INTO itempedido (id_pedido, id_produto, quantidade, preco_unitario) VALUES (?, ?, ?, ?)";
        // Produto sem controle de estoque (V11): a linha é encontrada mas o estoque fica igual.
        // A decisão usa o que está no banco, não o produto carregado na tela.
        String sqlEstoque = "UPDATE produto SET estoque = IF(controla_estoque, estoque - ?, estoque) "
                + "WHERE id_produto = ? AND (NOT controla_estoque OR estoque >= ?)";
        try (PreparedStatement stItem = conn.prepareStatement(sqlItem);
             PreparedStatement stEstoque = conn.prepareStatement(sqlEstoque)) {
            for (OrderItem item : itens) {
                stItem.setInt(1, idPedido);
                stItem.setInt(2, item.getProduto().getIdProduto());
                stItem.setInt(3, item.getQtd());
                stItem.setBigDecimal(4, item.getPrecoUnitario());
                stItem.addBatch();

                stEstoque.setInt(1, item.getQtd());
                stEstoque.setInt(2, item.getProduto().getIdProduto());
                stEstoque.setInt(3, item.getQtd());
                if (stEstoque.executeUpdate() == 0) {
                    // SQLException para cair no rollback do insert
                    throw new SQLException("Estoque insuficiente para o produto '" + item.getProduto().getNomeProduto() + "'.");
                }
            }
            stItem.executeBatch();
        }
    }

    private void inserirPagamento(Connection conn, Order obj, int idPedido, int idCaixa) throws SQLException {
        String sql = "INSERT INTO pagamento (id_pedido, valor_pago, metodo_pagamento, origem, id_caixa) VALUES (?, ?, ?, 'VENDA', ?)";
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            for (Order.PaymentSlice fatia : obj.getPagamentos()) {
                st.setInt(1, idPedido);
                st.setBigDecimal(2, fatia.getValor());
                st.setString(3, fatia.getMetodo().toString());
                st.setInt(4, idCaixa);
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    /**
     * Recebe um pagamento de fiado e abate dos pedidos em aberto, do mais antigo para o mais novo.
     * Um recebimento pode quitar vários pedidos: grava uma linha por pedido abatido, todas com a
     * mesma data/hora e forma de pagamento, para depois aparecerem juntas como um recebimento só.
     */
    @Override
    public void registerPartialPayment(Integer idCliente, BigDecimal valorPago, PaymentMethod metodo) {
        if (metodo == null || metodo == PaymentMethod.FIADO) {
            throw new IllegalArgumentException("Forma de pagamento inválida para receber fiado: " + metodo);
        }
        Timestamp agora = Timestamp.valueOf(LocalDateTime.now().withNano(0));

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stSelect = conn.prepareStatement(
                         "SELECT p.id_pedido, p.valor_total, " +
                                 "COALESCE((SELECT SUM(valor_pago) FROM pagamento WHERE id_pedido = p.id_pedido AND metodo_pagamento <> 'FIADO'), 0) AS ja_pago " +
                                 "FROM pedido p " +
                                 "WHERE p.id_cliente = ? AND p.status = 'FIADO' " +
                                 "ORDER BY p.data_pedido ASC");
                 PreparedStatement stInsert = conn.prepareStatement(
                         "INSERT INTO pagamento (id_pedido, valor_pago, metodo_pagamento, data_pagamento, origem, id_caixa) " +
                                 "VALUES (?, ?, ?, ?, 'RECEBIMENTO_FIADO', ?)");
                 PreparedStatement stUpdate = conn.prepareStatement(
                         "UPDATE pedido SET status = 'PAGO' WHERE id_pedido = ?")) {

                // O dinheiro do recebimento entra na gaveta: precisa de caixa aberto
                int idCaixa = idCaixaAberto(conn);
                stSelect.setInt(1, idCliente);

                try (ResultSet rs = stSelect.executeQuery()) {
                    BigDecimal valorRestante = Money.of(valorPago);

                    while (rs.next() && Money.isPositive(valorRestante)) {
                        int idPedido = rs.getInt("id_pedido");
                        BigDecimal valorTotalPedido = rs.getBigDecimal("valor_total");
                        BigDecimal jaPago = rs.getBigDecimal("ja_pago");
                        BigDecimal saldoPedido = valorTotalPedido.subtract(jaPago);

                        if (Money.isPositive(saldoPedido)) {
                            BigDecimal valorAbater = Money.min(valorRestante, saldoPedido);

                            stInsert.setInt(1, idPedido);
                            stInsert.setBigDecimal(2, valorAbater);
                            stInsert.setString(3, metodo.toString());
                            stInsert.setTimestamp(4, agora);
                            stInsert.setInt(5, idCaixa);
                            stInsert.executeUpdate();

                            valorRestante = valorRestante.subtract(valorAbater);

                            // comparação exata: com BigDecimal não há resto de arredondamento
                            if (saldoPedido.compareTo(valorAbater) == 0) {
                                stUpdate.setInt(1, idPedido);
                                stUpdate.executeUpdate();
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal getCustomerDebt(Integer idCliente) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT " +
                             "COALESCE((SELECT SUM(valor_total) FROM pedido WHERE id_cliente = ? AND status = 'FIADO'), 0) " +
                             "- " +
                             "COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg INNER JOIN pedido p ON pg.id_pedido = p.id_pedido WHERE p.id_cliente = ? AND p.status = 'FIADO' AND pg.metodo_pagamento <> 'FIADO'), 0) " +
                             "AS divida_total")) {
            st.setInt(1, idCliente);
            st.setInt(2, idCliente);

            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Money.of(rs.getBigDecimal("divida_total"));
                }
                return Money.ZERO;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Order> findById(Integer id) { return Optional.empty(); }
    @Override
    public List<Order> findAll() { return List.of(); }
    @Override
    public void update(Order obj) {}
    @Override
    public void deleteById(Integer id) {}
    @Override
    public Order instantiate(ResultSet rs) throws SQLException { return null; }

    /**
     * Pedidos fiado ainda em aberto, do mais antigo para o mais novo (mesma ordem em que os
     * recebimentos abatem), com quanto foi pago na hora da compra e quanto foi recebido depois.
     */
    @Override
    public List<Order> findOpenFiadoOrders(Integer idCliente) {
        String sql = "SELECT p.id_pedido, p.data_pedido, p.valor_total, " +
                "COALESCE(SUM(CASE WHEN pg.origem = 'VENDA' AND pg.metodo_pagamento <> 'FIADO' THEN pg.valor_pago END), 0) AS pago_na_venda, " +
                "COALESCE(SUM(CASE WHEN pg.origem = 'RECEBIMENTO_FIADO' THEN pg.valor_pago END), 0) AS recebido_depois " +
                "FROM pedido p LEFT JOIN pagamento pg ON pg.id_pedido = p.id_pedido " +
                "WHERE p.id_cliente = ? AND p.status = 'FIADO' " +
                "GROUP BY p.id_pedido, p.data_pedido, p.valor_total " +
                "ORDER BY p.data_pedido ASC";
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, idCliente);
            try (ResultSet rs = st.executeQuery()) {
                List<Order> list = new ArrayList<>();
                while (rs.next()) {
                    Order obj = new Order();
                    obj.setIdPedido(rs.getInt("id_pedido"));
                    obj.setDataPedido(rs.getTimestamp("data_pedido").toLocalDateTime());
                    obj.setValorTotal(rs.getBigDecimal("valor_total"));
                    obj.setStatus(OrderStatus.FIADO);
                    obj.setPagoNaVenda(rs.getBigDecimal("pago_na_venda"));
                    obj.setRecebidoDepois(rs.getBigDecimal("recebido_depois"));
                    list.add(obj);
                }
                return list;
            }
        } catch (SQLException e) { throw new DbException(e.getMessage(), e); }
    }

    /**
     * Recebimentos de fiado do cliente, do mais recente para o mais antigo. As linhas gravadas
     * no mesmo recebimento (mesma data/hora e forma) são somadas numa só.
     */
    @Override
    public List<Payment> findFiadoReceipts(Integer idCliente) {
        String sql = "SELECT pg.data_pagamento, pg.metodo_pagamento, SUM(pg.valor_pago) AS total, MAX(pg.id_caixa) AS id_caixa, " +
                "GROUP_CONCAT(CONCAT('#', pg.id_pedido) ORDER BY pg.id_pedido SEPARATOR ', ') AS pedidos " +
                "FROM pagamento pg JOIN pedido p ON p.id_pedido = pg.id_pedido " +
                "WHERE p.id_cliente = ? AND pg.origem = 'RECEBIMENTO_FIADO' " +
                "GROUP BY pg.data_pagamento, pg.metodo_pagamento " +
                "ORDER BY pg.data_pagamento DESC";
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, idCliente);
            try (ResultSet rs = st.executeQuery()) {
                List<Payment> list = new ArrayList<>();
                while (rs.next()) {
                    Payment obj = new Payment();
                    obj.setDataPagamento(rs.getTimestamp("data_pagamento").toLocalDateTime());
                    obj.setMetodo(PaymentMethod.valueOf(rs.getString("metodo_pagamento")));
                    obj.setValorPago(rs.getBigDecimal("total"));
                    obj.setOrigem(PaymentOrigin.RECEBIMENTO_FIADO);
                    obj.setPedidos(rs.getString("pedidos"));
                    int caixa = rs.getInt("id_caixa");
                    obj.setIdCaixa(rs.wasNull() ? null : caixa);
                    list.add(obj);
                }
                return list;
            }
        } catch (SQLException e) { throw new DbException(e.getMessage(), e); }
    }

    public OrderItem instantiateOI(ResultSet rs) throws SQLException {
        OrderItem obj = new OrderItem();
        obj.setQtd(rs.getInt("quantidade"));
        obj.setPrecoUnitario(rs.getBigDecimal("preco_unitario"));
        Product prod = new Product();
        prod.setCodBarras(rs.getString("cod_barras"));
        prod.setNomeProduto(rs.getString("nome_produto"));
        obj.setProduto(prod);
        Order ped = new Order();
        ped.setIdPedido(rs.getInt("id_pedido"));
        ped.setDataPedido(rs.getObject("data_pedido", LocalDateTime.class));
        obj.setPedido(ped);
        return obj;
    }

    @Override
    public List<Order> findPaidOrdersByCustomer(Integer idCliente) {
        return findOrdersByCustomerAndStatus(idCliente, OrderStatus.PAGO);
    }

    private List<Order> findOrdersByCustomerAndStatus(Integer idCliente, OrderStatus status) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT * FROM pedido WHERE id_cliente = ? AND status = ? ORDER BY data_pedido DESC")) {
            st.setInt(1, idCliente);
            st.setString(2, status.toString());
            try (ResultSet rs = st.executeQuery()) {
                List<Order> list = new ArrayList<>();
                while (rs.next()) {
                    Order obj = new Order();
                    obj.setIdPedido(rs.getInt("id_pedido"));
                    obj.setDataPedido(rs.getTimestamp("data_pedido").toLocalDateTime());
                    obj.setValorTotal(rs.getBigDecimal("valor_total"));
                    obj.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    list.add(obj);
                }
                return list;
            }
        } catch (SQLException e) { throw new DbException(e.getMessage(), e); }
    }

    @Override
    public List<OrderItem> findItemsByOrderId(int idPedido) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT i.*, p.nome_produto, p.cod_barras, ped.data_pedido FROM itempedido i " +
                     "JOIN produto p ON i.id_produto = p.id_produto JOIN pedido ped ON i.id_pedido = ped.id_pedido WHERE i.id_pedido = ?")) {
            st.setInt(1, idPedido);
            try (ResultSet rs = st.executeQuery()) {
                List<OrderItem> list = new ArrayList<>();
                while (rs.next()) { list.add(instantiateOI(rs)); }
                return list;
            }
        } catch (SQLException e) { throw new DbException(e.getMessage(), e); }
    }

    /**
     * Vendas do histórico, da mais recente para a mais antiga, com os filtros preenchidos.
     * O período usa "data >= início AND data < dia seguinte ao fim" (e não DATE(data) BETWEEN),
     * para o MySQL poder usar o índice idx_pedido_data.
     */
    @Override
    public List<SaleSummary> findSales(SaleFilter f, int limite) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id_pedido, p.data_pedido, p.valor_total, p.status, c.nome_cliente, " +
                "(SELECT COALESCE(SUM(i.quantidade), 0) FROM itempedido i WHERE i.id_pedido = p.id_pedido) AS qtd_itens, " +
                "CONCAT_WS(',', " +
                "  (SELECT GROUP_CONCAT(DISTINCT pg.metodo_pagamento SEPARATOR ',') FROM pagamento pg " +
                "     WHERE pg.id_pedido = p.id_pedido AND pg.origem = 'VENDA'), " +
                "  (SELECT GROUP_CONCAT(DISTINCT pe.metodo_pagamento SEPARATOR ',') FROM pagamento_estornado pe " +
                "     WHERE pe.id_pedido = p.id_pedido AND pe.origem = 'VENDA')) AS formas, " +
                "p.desconto, p.motivo_cancelamento, p.id_pedido_troca " +
                "FROM pedido p LEFT JOIN cliente c ON c.id_cliente = p.id_cliente " +
                "WHERE p.data_pedido >= ? AND p.data_pedido < ?");
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(f.getInicio().atStartOfDay()));
        params.add(Timestamp.valueOf(f.getFim().plusDays(1).atStartOfDay()));

        if (f.getForma() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM pagamento pg WHERE pg.id_pedido = p.id_pedido " +
                    "AND pg.origem = 'VENDA' AND pg.metodo_pagamento = ?)");
            params.add(f.getForma().toString());
        }
        if (f.getStatus() != null) {
            sql.append(" AND p.status = ?");
            params.add(f.getStatus().toString());
        }
        if (f.getCliente() != null) {
            sql.append(" AND c.nome_cliente LIKE ?");
            params.add("%" + f.getCliente() + "%");
        }
        if (f.getProduto() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM itempedido i JOIN produto pr ON pr.id_produto = i.id_produto " +
                    "WHERE i.id_pedido = p.id_pedido AND (pr.nome_produto LIKE ? OR pr.cod_barras = ?))");
            params.add("%" + f.getProduto() + "%");
            params.add(f.getProduto());
        }
        sql.append(" ORDER BY p.data_pedido DESC, p.id_pedido DESC LIMIT ?");
        params.add(limite);

        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                st.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = st.executeQuery()) {
                List<SaleSummary> list = new ArrayList<>();
                while (rs.next()) {
                    SaleSummary s = new SaleSummary();
                    s.setIdPedido(rs.getInt("id_pedido"));
                    s.setDataPedido(rs.getTimestamp("data_pedido").toLocalDateTime());
                    s.setValorTotal(rs.getBigDecimal("valor_total"));
                    s.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    s.setNomeCliente(rs.getString("nome_cliente"));
                    s.setQuantidadeItens(rs.getInt("qtd_itens"));
                    List<PaymentMethod> formas = new ArrayList<>();
                    String texto = rs.getString("formas");
                    if (texto != null && !texto.isEmpty()) {
                        for (String t : texto.split(",")) {
                            PaymentMethod forma = PaymentMethod.ler(t);
                            if (forma != null && !formas.contains(forma)) formas.add(forma);
                        }
                    }
                    s.setDesconto(rs.getBigDecimal("desconto"));
                    s.setMotivoCancelamento(rs.getString("motivo_cancelamento"));
                    int troca = rs.getInt("id_pedido_troca");
                    s.setIdPedidoTroca(rs.wasNull() ? null : troca);
                    s.setFormas(formas);
                    list.add(s);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    /** Todos os pagamentos de um pedido: os da hora da venda e os recebimentos de fiado depois. */
    @Override
    public List<Payment> findPaymentsByOrder(int idPedido) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT metodo_pagamento, valor_pago, origem, data_pagamento, id_pagamento, 0 AS estornado FROM pagamento WHERE id_pedido = ? " +
                             "UNION ALL " +
                             "SELECT metodo_pagamento, valor_pago, origem, data_pagamento, id_pagamento, 1 FROM pagamento_estornado WHERE id_pedido = ? " +
                             "ORDER BY data_pagamento, id_pagamento")) {
            st.setInt(1, idPedido);
            st.setInt(2, idPedido);
            try (ResultSet rs = st.executeQuery()) {
                List<Payment> list = new ArrayList<>();
                while (rs.next()) {
                    Payment p = new Payment();
                    p.setEstornado(rs.getInt("estornado") == 1);
                    p.setMetodo(PaymentMethod.ler(rs.getString("metodo_pagamento")));
                    p.setValorPago(rs.getBigDecimal("valor_pago"));
                    p.setOrigem(PaymentOrigin.valueOf(rs.getString("origem")));
                    p.setDataPagamento(rs.getTimestamp("data_pagamento").toLocalDateTime());
                    list.add(p);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    // ===================== Cancelamento, troca e estorno (V13) =====================

    static final String TIPO_CANCELAMENTO = "CANCELAMENTO";
    static final String TIPO_TROCA = "TROCA";
    static final String TIPO_ESTORNO_RECEBIMENTO = "ESTORNO_RECEBIMENTO";

    public static final String MENSAGEM_SO_CAIXA_ABERTO =
            "Só dá para desfazer vendas e recebimentos do caixa que está aberto agora.";

    /** Quando o caixa aberto foi aberto (vendas a partir daí são "do caixa aberto"). */
    private LocalDateTime aberturaDoCaixa(Connection conn) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement("SELECT aberto_em FROM caixa WHERE fechado_em IS NULL");
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) return rs.getTimestamp(1).toLocalDateTime();
        }
        throw new SQLException(MENSAGEM_CAIXA_FECHADO);
    }

    /** Quanto a venda teve em cada forma: tudo que foi pago (na hora e depois) + o que ficou no fiado. */
    private Map<PaymentMethod, BigDecimal> pagoPorForma(Connection conn, int idPedido, BigDecimal valorTotal) throws SQLException {
        Map<PaymentMethod, BigDecimal> m = new EnumMap<>(PaymentMethod.class);
        BigDecimal pago = Money.ZERO;
        try (PreparedStatement st = conn.prepareStatement(
                "SELECT metodo_pagamento, SUM(valor_pago) FROM pagamento " +
                "WHERE id_pedido = ? AND metodo_pagamento <> 'FIADO' GROUP BY metodo_pagamento")) {
            st.setInt(1, idPedido);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    BigDecimal v = Money.of(rs.getBigDecimal(2));
                    // forma antiga desconhecida ("PAGO"): conta como dinheiro, que é o que volta da gaveta
                    PaymentMethod forma = PaymentMethod.ler(rs.getString(1));
                    m.merge(forma == null ? PaymentMethod.DINHEIRO : forma, v, BigDecimal::add);
                    pago = pago.add(v);
                }
            }
        }
        BigDecimal fiado = Money.max(Money.ZERO, Money.of(valorTotal).subtract(pago));
        if (Money.isPositive(fiado)) m.put(PaymentMethod.FIADO, fiado);
        return m;
    }

    /** Move pagamentos para pagamento_estornado (arquivo) e apaga da tabela pagamento. */
    private void arquivarPagamentos(Connection conn, String where, Object[] params, String tipo, String motivo) throws SQLException {
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO pagamento_estornado (id_pagamento, id_pedido, valor_pago, data_pagamento, metodo_pagamento, " +
                "origem, id_caixa, estornado_em, tipo, motivo) " +
                "SELECT id_pagamento, id_pedido, valor_pago, data_pagamento, metodo_pagamento, origem, id_caixa, NOW(), ?, ? " +
                "FROM pagamento WHERE " + where);
             PreparedStatement del = conn.prepareStatement("DELETE FROM pagamento WHERE " + where)) {
            ins.setString(1, tipo);
            ins.setString(2, motivo);
            for (int i = 0; i < params.length; i++) {
                ins.setObject(i + 3, params[i]);
                del.setObject(i + 1, params[i]);
            }
            ins.executeUpdate();
            del.executeUpdate();
        }
    }

    /**
     * Cancela dentro de uma transação já aberta (usado no cancelamento e na troca).
     * Confere: venda existe, não está cancelada, é do caixa aberto. Devolve o estoque,
     * arquiva os pagamentos e marca a venda CANCELADO.
     */
    private Map<PaymentMethod, BigDecimal> cancelarNaTransacao(Connection conn, int idPedido, String motivo, String tipo) throws SQLException {
        LocalDateTime abertura = aberturaDoCaixa(conn);
        BigDecimal valorTotal;
        try (PreparedStatement st = conn.prepareStatement(
                "SELECT status, data_pedido, valor_total FROM pedido WHERE id_pedido = ? FOR UPDATE")) {
            st.setInt(1, idPedido);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) throw new SQLException("Venda #" + idPedido + " não encontrada.");
                if (OrderStatus.CANCELADO.name().equals(rs.getString("status"))) {
                    throw new SQLException("A venda #" + idPedido + " já foi cancelada.");
                }
                if (rs.getTimestamp("data_pedido").toLocalDateTime().isBefore(abertura)) {
                    throw new SQLException(MENSAGEM_SO_CAIXA_ABERTO);
                }
                valorTotal = rs.getBigDecimal("valor_total");
            }
        }

        Map<PaymentMethod, BigDecimal> pago = pagoPorForma(conn, idPedido, valorTotal);

        // Estoque volta (só de quem controla estoque). Soma por produto: o mesmo produto pode estar em 2 linhas.
        try (PreparedStatement st = conn.prepareStatement(
                "UPDATE produto pr SET pr.estoque = pr.estoque + " +
                "(SELECT SUM(i.quantidade) FROM itempedido i WHERE i.id_pedido = ? AND i.id_produto = pr.id_produto) " +
                "WHERE pr.controla_estoque AND pr.id_produto IN (SELECT i2.id_produto FROM itempedido i2 WHERE i2.id_pedido = ?)")) {
            st.setInt(1, idPedido);
            st.setInt(2, idPedido);
            st.executeUpdate();
        }

        arquivarPagamentos(conn, "id_pedido = ?", new Object[]{idPedido}, tipo, motivo);

        try (PreparedStatement st = conn.prepareStatement(
                "UPDATE pedido SET status = 'CANCELADO', cancelado_em = NOW(), motivo_cancelamento = ? WHERE id_pedido = ?")) {
            st.setString(1, motivo);
            st.setInt(2, idPedido);
            st.executeUpdate();
        }

        Map<PaymentMethod, BigDecimal> devolver = new EnumMap<>(PaymentMethod.class);
        pago.forEach((forma, v) -> { if (forma != PaymentMethod.FIADO) devolver.put(forma, v); });
        return devolver;
    }

    @Override
    public Map<PaymentMethod, BigDecimal> cancel(int idPedido, String motivo) {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<PaymentMethod, BigDecimal> devolver = cancelarNaTransacao(conn, idPedido, motivo, TIPO_CANCELAMENTO);
                conn.commit();
                return devolver;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal reverseFiadoReceipt(int idCliente, LocalDateTime dataPagamento, PaymentMethod metodo, String motivo) {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int idCaixa = idCaixaAberto(conn);
                // As linhas do recebimento: mesma data/hora e forma, em pedidos do cliente
                List<Integer> idsPagamento = new ArrayList<>();
                List<Integer> idsPedido = new ArrayList<>();
                BigDecimal total = Money.ZERO;
                try (PreparedStatement st = conn.prepareStatement(
                        "SELECT pg.id_pagamento, pg.id_pedido, pg.id_caixa, pg.valor_pago FROM pagamento pg " +
                        "JOIN pedido p ON p.id_pedido = pg.id_pedido " +
                        "WHERE p.id_cliente = ? AND pg.origem = 'RECEBIMENTO_FIADO' " +
                        "AND pg.data_pagamento = ? AND pg.metodo_pagamento = ? FOR UPDATE")) {
                    st.setInt(1, idCliente);
                    st.setTimestamp(2, Timestamp.valueOf(dataPagamento));
                    st.setString(3, metodo.name());
                    try (ResultSet rs = st.executeQuery()) {
                        while (rs.next()) {
                            if (rs.getInt("id_caixa") != idCaixa) throw new SQLException(MENSAGEM_SO_CAIXA_ABERTO);
                            idsPagamento.add(rs.getInt("id_pagamento"));
                            idsPedido.add(rs.getInt("id_pedido"));
                            total = total.add(Money.of(rs.getBigDecimal("valor_pago")));
                        }
                    }
                }
                if (idsPagamento.isEmpty()) throw new SQLException("Recebimento não encontrado (talvez já tenha sido estornado).");

                for (int id : idsPagamento) {
                    arquivarPagamentos(conn, "id_pagamento = ?", new Object[]{id}, TIPO_ESTORNO_RECEBIMENTO, motivo);
                }
                // Compras que esse recebimento tinha quitado voltam a dever
                try (PreparedStatement st = conn.prepareStatement(
                        "UPDATE pedido SET status = 'FIADO' WHERE id_pedido = ? AND status = 'PAGO'")) {
                    for (int id : idsPedido) {
                        st.setInt(1, id);
                        st.executeUpdate();
                    }
                }
                conn.commit();
                return total;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<VendaParaDesfazer> findParaDesfazer(int idPedido) {
        try (Connection conn = DB.getConnection()) {
            VendaParaDesfazer v = new VendaParaDesfazer();
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT p.*, (SELECT aberto_em FROM caixa WHERE fechado_em IS NULL) AS abertura " +
                    "FROM pedido p WHERE p.id_pedido = ?")) {
                st.setInt(1, idPedido);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    v.setIdPedido(idPedido);
                    int cliente = rs.getInt("id_cliente");
                    v.setIdCliente(rs.wasNull() ? null : cliente);
                    v.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    v.setDataPedido(rs.getTimestamp("data_pedido").toLocalDateTime());
                    v.setValorTotal(rs.getBigDecimal("valor_total"));
                    v.setDesconto(rs.getBigDecimal("desconto"));
                    Timestamp abertura = rs.getTimestamp("abertura");
                    v.setDoCaixaAberto(abertura != null && !v.getDataPedido().isBefore(abertura.toLocalDateTime()));
                }
            }
            ProductDaoJdbc produtos = new ProductDaoJdbc();
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT i.quantidade, i.preco_unitario, pr.* FROM itempedido i " +
                    "JOIN produto pr ON pr.id_produto = i.id_produto WHERE i.id_pedido = ? ORDER BY i.id_item")) {
                st.setInt(1, idPedido);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        v.getItens().add(new OrderItem(produtos.instantiate(rs), rs.getInt("quantidade"), rs.getBigDecimal("preco_unitario")));
                    }
                }
            }
            if (!v.isCancelada()) v.getPagoPorForma().putAll(pagoPorForma(conn, idPedido, v.getValorTotal()));
            return Optional.of(v);
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
