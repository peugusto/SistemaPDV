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

    private Connection conn;

    public CustomerDaoJdbc(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insert(Customer obj) {
        PreparedStatement st = null;
        try {
            st = conn.prepareStatement(
                    "INSERT INTO cliente (nome_cliente, limite_credito, situacao_fiado) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);

            st.setString(1, obj.getNomeCliente());
            st.setDouble(2, obj.getLimiteCredito());
            st.setString(3, obj.getSituacaoFiado().toString());

            int rowsAffected = st.executeUpdate();

            if (rowsAffected > 0) {
                ResultSet rs = st.getGeneratedKeys();
                if (rs.next()) {
                    obj.setIdCliente(rs.getInt(1));
                }
                DB.closeResultSet(rs);
            } else {
                throw new DbException("Erro inesperado! Nenhum cliente inserido.");
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
        }
    }

    @Override
    public Optional<Customer> findById(Integer id) {
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement("SELECT * FROM cliente WHERE id_cliente = ?");
            st.setInt(1, id);
            rs = st.executeQuery();
            if (rs.next()) {
                return Optional.of(instantiate(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
            DB.closeResultSet(rs);
        }
    }

    @Override
    public List<Customer> findAll() {
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement("SELECT * FROM cliente ORDER BY nome_cliente");
            rs = st.executeQuery();
            List<Customer> list = new ArrayList<>();
            while (rs.next()) {
                list.add(instantiate(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
            DB.closeResultSet(rs);
        }
    }

    @Override
    public void update(Customer obj) {
        PreparedStatement st = null;
        try {
            st = conn.prepareStatement(
                    "UPDATE cliente SET nome_cliente = ?, limite_credito = ?, situacao_fiado = ? WHERE id_cliente = ?");

            st.setString(1, obj.getNomeCliente());
            st.setDouble(2, obj.getLimiteCredito());
            st.setString(3, obj.getSituacaoFiado().toString());
            st.setInt(4, obj.getIdCliente());

            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
        }
    }

    @Override
    public void deleteById(Integer id) {
        PreparedStatement st = null;
        try {
            st = conn.prepareStatement("DELETE FROM cliente WHERE id_cliente = ?");
            st.setInt(1, id);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
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