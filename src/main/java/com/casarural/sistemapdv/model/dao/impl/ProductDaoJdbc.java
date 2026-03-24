package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDaoJdbc implements ProductDao {

    private Connection conn;

    public ProductDaoJdbc(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insert(Product obj) {
        PreparedStatement st = null;
        try {
            st = conn.prepareStatement(
                    "INSERT INTO produto (cod_barras, nome_produto, preco_produto, estoque) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);

            st.setString(1, obj.getCodBarras());
            st.setString(2, obj.getNomeProduto());
            st.setDouble(3, obj.getPrecoProduto());
            st.setInt(4, obj.getEstoque());

            int rowsAffected = st.executeUpdate();

            if (rowsAffected > 0) {
                ResultSet rs = st.getGeneratedKeys();
                if (rs.next()) {
                    int id = rs.getInt(1);
                    obj.setIdProduto(id);
                }
                DB.closeResultSet(rs);
            } else {
                throw new DbException("Erro inesperado! Nenhuma linha alterada.");
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
        }
    }

    @Override
    public Optional<Product> findById(Integer id) {
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement("SELECT * FROM produto WHERE id_produto = ?");
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
    public List<Product> findAll() {
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement("SELECT * FROM produto ORDER BY nome_produto");
            rs = st.executeQuery();

            List<Product> list = new ArrayList<>();
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
    public void update(Product obj) {
        PreparedStatement st = null;
        try {
            st = conn.prepareStatement(
                    "UPDATE produto SET cod_barras = ?, nome_produto = ?, preco_produto = ?, estoque = ? WHERE id_produto = ?");

            st.setString(1, obj.getCodBarras());
            st.setString(2, obj.getNomeProduto());
            st.setDouble(3, obj.getPrecoProduto());
            st.setInt(4, obj.getEstoque());
            st.setInt(5, obj.getIdProduto());

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
            st = conn.prepareStatement("DELETE FROM produto WHERE id_produto = ?");
            st.setInt(1, id);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage());
        } finally {
            DB.closeStatement(st);
        }
    }


    public Product instantiate(ResultSet rs) throws SQLException {
        Product prod = new Product();
        prod.setIdProduto(rs.getInt("id_produto"));
        prod.setCodBarras(rs.getString("cod_barras"));
        prod.setNomeProduto(rs.getString("nome_produto"));
        prod.setPrecoProduto(rs.getDouble("preco_produto"));

        prod.setEstoque(rs.getInt("estoque"));
        return prod;
    }

    @Override
    public Optional<Product> findByCodBarras(String codBarras) {
        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement("SELECT * FROM produto WHERE cod_barras = ?");
            st.setString(1, codBarras);
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
}