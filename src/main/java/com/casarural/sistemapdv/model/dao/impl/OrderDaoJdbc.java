package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderDaoJdbc implements OrderDao {

    @Override
    public void insert(Order obj) {
        // Uma única conexão do início ao fim da transação: pedido + itens + estoque + pagamentos
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement st = conn.prepareStatement(
                    "INSERT INTO pedido (id_cliente, valor_total, status) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                if (obj.getCostumer() != null) {
                    st.setInt(1, obj.getCostumer().getIdCliente());
                } else {
                    st.setNull(1, Types.INTEGER);
                }
                st.setDouble(2, obj.getValorTotal());
                st.setString(3, obj.getStatus().toString());

                int rowsAffected = st.executeUpdate();
                if (rowsAffected > 0) {
                    try (ResultSet rs = st.getGeneratedKeys()) {
                        if (rs.next()) {
                            int idPedido = rs.getInt(1);
                            obj.setIdPedido(idPedido);
                            inserirItensEBaixarEstoque(conn, obj.getItemPedido(), idPedido);
                            inserirPagamento(conn, obj, idPedido);
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
            throw new DbException(e.getMessage());
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
                stItem.setDouble(4, item.getPrecoUnitario());
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

    private void inserirPagamento(Connection conn, Order obj, int idPedido) throws SQLException {
        String sql = "INSERT INTO pagamento (id_pedido, valor_pago, metodo_pagamento) VALUES (?, ?, ?)";
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            for (Order.PaymentSlice fatia : obj.getPagamentos()) {
                st.setInt(1, idPedido);
                st.setDouble(2, fatia.getValor());
                st.setString(3, fatia.getMetodo().toString());
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    @Override
    public void registerPartialPayment(Integer idCliente, double valorPago) {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stSelect = conn.prepareStatement(
                         "SELECT p.id_pedido, p.valor_total, " +
                                 "COALESCE((SELECT SUM(valor_pago) FROM pagamento WHERE id_pedido = p.id_pedido AND metodo_pagamento <> 'FIADO'), 0) AS ja_pago " +
                                 "FROM pedido p " +
                                 "WHERE p.id_cliente = ? AND p.status = 'FIADO' " +
                                 "ORDER BY p.data_pedido ASC");
                 PreparedStatement stInsert = conn.prepareStatement(
                         "INSERT INTO pagamento (id_pedido, valor_pago, metodo_pagamento) VALUES (?, ?, 'DINHEIRO')");
                 PreparedStatement stUpdate = conn.prepareStatement(
                         "UPDATE pedido SET status = 'PAGO' WHERE id_pedido = ?")) {

                stSelect.setInt(1, idCliente);

                try (ResultSet rs = stSelect.executeQuery()) {
                    double valorRestante = valorPago;

                    while (rs.next() && valorRestante > 0) {
                        int idPedido = rs.getInt("id_pedido");
                        double valorTotalPedido = rs.getDouble("valor_total");
                        double jaPago = rs.getDouble("ja_pago");
                        double saldoPedido = valorTotalPedido - jaPago;

                        if (saldoPedido > 0) {
                            double valorAbater = Math.min(valorRestante, saldoPedido);

                            stInsert.setInt(1, idPedido);
                            stInsert.setDouble(2, valorAbater);
                            stInsert.executeUpdate();

                            valorRestante -= valorAbater;

                            if (Math.abs(saldoPedido - valorAbater) < 0.009) {
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
            throw new DbException(e.getMessage());
        }
    }

    @Override
    public double getCustomerDebt(Integer idCliente) {
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
                    return rs.getDouble("divida_total");
                }
                return 0.0;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
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

    @Override
    public void payFullDebt(Integer idCliente) {
        double totalDevendo = getCustomerDebt(idCliente);
        if (totalDevendo > 0) {
            registerPartialPayment(idCliente, totalDevendo);
        }
    }

    @Override
    public List<OrderItem> findItemsByCustomerPending(Integer idCliente) {
        String sql = "SELECT p.id_pedido, p.data_pedido, prod.nome_produto, prod.cod_barras, i.quantidade, i.preco_unitario " +
                "FROM itempedido i JOIN pedido p ON i.id_pedido = p.id_pedido JOIN produto prod ON i.id_produto = prod.id_produto " +
                "WHERE p.id_cliente = ? AND p.status = 'FIADO' ORDER BY p.data_pedido DESC";
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, idCliente);
            try (ResultSet rs = st.executeQuery()) {
                List<OrderItem> list = new ArrayList<>();
                while (rs.next()) { list.add(instantiateOI(rs)); }
                return list;
            }
        } catch (SQLException e) { throw new DbException(e.getMessage()); }
    }

    public OrderItem instantiateOI(ResultSet rs) throws SQLException {
        OrderItem obj = new OrderItem();
        obj.setQtd(rs.getInt("quantidade"));
        obj.setPrecoUnitario(rs.getDouble("preco_unitario"));
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
    public List<Order> findByCustomerPending(Integer idCliente) {
        return findOrdersByCustomerAndStatus(idCliente, OrderStatus.FIADO);
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
                    obj.setValorTotal(rs.getDouble("valor_total"));
                    obj.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    list.add(obj);
                }
                return list;
            }
        } catch (SQLException e) { throw new DbException(e.getMessage()); }
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
        } catch (SQLException e) { throw new DbException(e.getMessage()); }
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
                    item.setPrecoUnitario(rs.getDouble("preco_unitario"));
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
            throw new DbException(e.getMessage());
        }
    }
}
