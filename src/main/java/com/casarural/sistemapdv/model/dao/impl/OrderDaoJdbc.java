package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
                    "INSERT INTO pedido (id_cliente, valor_total, status) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                int idCaixa = idCaixaAberto(conn);   // antes de gravar qualquer coisa

                if (obj.getCostumer() != null) {
                    st.setInt(1, obj.getCostumer().getIdCliente());
                } else {
                    st.setNull(1, Types.INTEGER);
                }
                st.setBigDecimal(2, obj.getValorTotal());
                st.setString(3, obj.getStatus().toString());

                int rowsAffected = st.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet rs = st.getGeneratedKeys()) {
                        if (rs.next()) {
                            int idPedido = rs.getInt(1);
                            obj.setIdPedido(idPedido);
                            inserirItensEBaixarEstoque(conn, obj.getItemPedido(), idPedido);
                            inserirPagamento(conn, obj, idPedido, idCaixa);
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
        String sqlEstoque = "UPDATE produto SET estoque = estoque - ? WHERE id_produto = ? AND estoque >= ?";
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
        String sql = "SELECT pg.data_pagamento, pg.metodo_pagamento, SUM(pg.valor_pago) AS total, " +
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

    public List<OrderItem> findItemsByDate(LocalDate inicio, LocalDate fim) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT itempedido.*, produto.nome_produto, produto.cod_barras, pedido.data_pedido " +
                             "FROM itempedido " +
                             "INNER JOIN produto ON itempedido.id_produto = produto.id_produto " +
                             "INNER JOIN pedido ON itempedido.id_pedido = pedido.id_pedido " +
                             "WHERE DATE(pedido.data_pedido) BETWEEN ? AND ? " +
                             "ORDER BY pedido.data_pedido DESC")) {

            st.setDate(1, java.sql.Date.valueOf(inicio));
            st.setDate(2, java.sql.Date.valueOf(fim));

            try (ResultSet rs = st.executeQuery()) {
                List<OrderItem> list = new ArrayList<>();

                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setPrecoUnitario(rs.getBigDecimal("preco_unitario"));
                    item.setQtd(rs.getInt("quantidade"));

                    Product prod = new Product();
                    prod.setNomeProduto(rs.getString("nome_produto"));
                    prod.setCodBarras(rs.getString("cod_barras"));
                    item.setProduto(prod);

                    Order pedido = new Order();
                    pedido.setIdPedido(rs.getInt("id_pedido"));
                    pedido.setDataPedido(rs.getTimestamp("data_pedido").toLocalDateTime());
                    item.setPedido(pedido);

                    list.add(item);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
