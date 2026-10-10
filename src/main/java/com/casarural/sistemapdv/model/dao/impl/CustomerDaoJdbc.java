package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CustomerDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDaoJdbc implements CustomerDao {

    @Override
    public void insert(Customer obj) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO cliente (nome_cliente, limite_credito, situacao_fiado) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {

            st.setString(1, obj.getNomeCliente());
            st.setDouble(2, obj.getLimiteCredito());
            st.setString(3, obj.getSituacaoFiado().toString());

            int rowsAffected = st.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet rs = st.getGeneratedKeys()) {
                    if (rs.next()) {
                        obj.setIdCliente(rs.getInt(1));
                    }
                }
            } else {
                throw new DbException("Erro inesperado! Nenhum cliente inserido.");
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        }
    }

    @Override
    public Optional<Customer> findById(Integer id) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM cliente WHERE id_cliente = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(instantiate(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        }
    }

    @Override
    public List<Customer> findAll() {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT c.*, " +
                             "COALESCE((SELECT SUM(p.valor_total) FROM pedido p WHERE p.id_cliente = c.id_cliente AND p.status = 'FIADO'), 0) " +
                             "- " +
                             "COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg INNER JOIN pedido p ON pg.id_pedido = p.id_pedido WHERE p.id_cliente = c.id_cliente AND p.status = 'FIADO' AND pg.metodo_pagamento <> 'FIADO'), 0) " +
                             "AS total_devendo " +
                             "FROM cliente c ORDER BY nome_cliente");
             ResultSet rs = st.executeQuery()) {

            List<Customer> list = new ArrayList<>();
            while (rs.next()) {
                Customer obj = instantiate(rs);
                obj.setTotalDevendo(rs.getDouble("total_devendo"));
                list.add(obj);
            }
            return list;
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        }
    }

    @Override
    public void update(Customer obj) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE cliente SET nome_cliente = ?, limite_credito = ?, situacao_fiado = ? WHERE id_cliente = ?")) {

            st.setString(1, obj.getNomeCliente());
            st.setDouble(2, obj.getLimiteCredito());
            st.setString(3, obj.getSituacaoFiado().toString());
            st.setInt(4, obj.getIdCliente());

            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        }
    }

    @Override
    public void deleteById(Integer id) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("DELETE FROM cliente WHERE id_cliente = ?")) {
            st.setInt(1, id);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        }
    }

    public Customer instantiate(ResultSet rs) throws SQLException {
        Customer obj = new Customer();
        obj.setIdCliente(rs.getInt("id_cliente"));
        obj.setNomeCliente(rs.getString("nome_cliente"));
        obj.setLimiteCredito(rs.getDouble("limite_credito"));
        obj.setSituacaoFiado(CustomerStatus.valueOf(rs.getString("situacao_fiado")));
        return obj;
    }
}
