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
    private Connection conn;

    public OrderDaoJdbc(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insert(Order obj) {
        PreparedStatement st = null;
        try {
            conn.setAutoCommit(false);

            st = conn.prepareStatement(
                    "INSERT INTO pedido (id_cliente, valor_total, status) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);

            if (obj.getCostumer() != null) {
                st.setInt(1, obj.getCostumer().getIdCliente());
            } else {
                st.setNull(1, Types.INTEGER);
            }
            st.setDouble(2, obj.getValorTotal());
            st.setString(3, obj.getStatus().toString());

            int rowsAffected = st.executeUpdate();

            if (rowsAffected > 0) {
                ResultSet rs = st.getGeneratedKeys();
                if (rs.next()) {
                    int idPedido = rs.getInt(1);
                    obj.setIdPedido(idPedido);

                    inserirItensEBaixarEstoque(obj.getItemPedido(), idPedido);
                    inserirPagamento(obj, idPedido);
                }
            }

            conn.commit();

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                throw new DbException("Erro crítico no Rollback: " + rollbackEx.getMessage());
            }
            throw new DbException("Erro ao finalizar venda: " + e.getMessage());
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
            DB.closeStatement(st);
        }
    }

    private void inserirItensEBaixarEstoque(List<OrderItem> itens, int idPedido) throws SQLException {
        String sqlItem = "INSERT INTO itempedido (id_pedido, id_produto, quantidade, preco_unitario) VALUES (?, ?, ?, ?)";
        String sqlEstoque = "UPDATE produto SET estoque = estoque - ? WHERE id_produto = ?";

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
                stEstoque.addBatch();
            }

            stItem.executeBatch();
            stEstoque.executeBatch();
        }
    }

    private void inserirPagamento(Order obj, int idPedido) throws SQLException {
        String sql = "INSERT INTO pagamento (id_pedido, valor_pago, metodo_pagamento) VALUES (?, ?, ?)";
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setInt(1, idPedido);
            st.setDouble(2, obj.getValorTotal());
            st.setString(3, obj.getStatus().toString());
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        }
    }

    @Override
    public Optional<Order> findById(Integer id) {
        return Optional.empty();
    }

    @Override
    public List<Order> findAll() {
        return List.of();
    }

    @Override
    public void update(Order obj) {

    }

    @Override
    public void deleteById(Integer id) {

    }

    @Override
    public Order instantiate(ResultSet rs) throws SQLException {
        return null;
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
        ped.setDataPedido(rs.getObject("data_pedido",LocalDateTime.class));

        obj.setPedido(ped);

        return obj;
    }
    @Override
    public List<Order> findByCustomerPending(Integer idCliente) {
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement(
                    "SELECT * FROM pedido WHERE id_cliente = ? AND status = 'FIADO' ORDER BY data_pedido DESC"
            );
            st.setInt(1, idCliente);
            rs = st.executeQuery();

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
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeResultSet(rs);
            DB.closeStatement(st);
        }
    }


    @Override
    public List<OrderItem> findItemsByDate(LocalDate inicio, LocalDate fim) {
        PreparedStatement st = null;
        ResultSet rs = null;

        String sql = "SELECT \n" +
                "    p.data_pedido, \n" +
                "    p.id_pedido, \n" +
                "    prod.nome_produto, \n" +
                "    prod.cod_barras,\n" +
                "    i.quantidade, \n" +
                "    i.preco_unitario, \n" +
                "    (i.quantidade * i.preco_unitario) AS subtotal\n" +
                "FROM itempedido i\n" +
                "JOIN pedido p ON i.id_pedido = p.id_pedido\n" +
                "JOIN produto prod ON i.id_produto = prod.id_produto\n" +
                "WHERE p.data_pedido BETWEEN ? AND ?\n" +
                "ORDER BY p.data_pedido DESC;";

        try{
           st = conn.prepareStatement(sql);
           st.setDate(1,Date.valueOf(inicio));
           st.setDate(2, Date.valueOf(fim));

            rs = st.executeQuery();
            List<OrderItem> oiList = new ArrayList<>();

            while(rs.next()){
                oiList.add(instantiateOI(rs));
            }
            return oiList;

        }catch (SQLException e){
            throw new DbException(e.getMessage());
        }finally {
            DB.closeStatement(st);
        }
    }
}