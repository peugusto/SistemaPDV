package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDaoJdbc implements ProductDao {

    @Override
    public void insert(Product obj) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO produto (cod_barras, nome_produto, preco_produto, estoque, permite_fiado, tipo_venda, controla_estoque, preco_custo, ativo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {

            st.setString(1, obj.getCodBarras());
            st.setString(2, obj.getNomeProduto());
            st.setBigDecimal(3, obj.getPrecoProduto());
            st.setInt(4, obj.getEstoque());
            st.setBoolean(5, obj.isPermiteFiado());
            st.setString(6, obj.getTipoVenda().name());
            st.setBoolean(7, obj.isControlaEstoque());
            st.setBigDecimal(8, obj.getPrecoCusto());
            st.setBoolean(9, obj.isAtivo());

            int rowsAffected = st.executeUpdate();

            if (rowsAffected > 0) {
                try (ResultSet rs = st.getGeneratedKeys()) {
                    if (rs.next()) {
                        obj.setIdProduto(rs.getInt(1));
                    }
                }
            } else {
                throw new DbException("Erro inesperado! Nenhuma linha alterada.");
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Product> findById(Integer id) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM produto WHERE id_produto = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(instantiate(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public List<Product> findAll() {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM produto ORDER BY nome_produto");
             ResultSet rs = st.executeQuery()) {

            List<Product> list = new ArrayList<>();
            while (rs.next()) {
                list.add(instantiate(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void update(Product obj) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE produto SET cod_barras = ?, nome_produto = ?, preco_produto = ?, estoque = ?, permite_fiado = ?, "
                             + "tipo_venda = ?, controla_estoque = ?, preco_custo = ?, ativo = ? WHERE id_produto = ?")) {

            st.setString(1, obj.getCodBarras());
            st.setString(2, obj.getNomeProduto());
            st.setBigDecimal(3, obj.getPrecoProduto());
            st.setInt(4, obj.getEstoque());
            st.setBoolean(5, obj.isPermiteFiado());
            st.setString(6, obj.getTipoVenda().name());
            st.setBoolean(7, obj.isControlaEstoque());
            st.setBigDecimal(8, obj.getPrecoCusto());
            st.setBoolean(9, obj.isAtivo());
            st.setInt(10, obj.getIdProduto());

            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(Integer id) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("DELETE FROM produto WHERE id_produto = ?")) {
            st.setInt(1, id);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }


    /** Em quantas vendas e compras de pão o produto aparece (se > 0, não pode ser excluído). */
    @Override
    public int contarUsos(int idProduto) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT (SELECT COUNT(*) FROM itempedido WHERE id_produto = ?) " +
                     "     + (SELECT COUNT(*) FROM compra_pao WHERE id_produto = ?)")) {
            st.setInt(1, idProduto);
            st.setInt(2, idProduto);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    public Product instantiate(ResultSet rs) throws SQLException {
        Product prod = new Product();
        prod.setIdProduto(rs.getInt("id_produto"));
        prod.setCodBarras(rs.getString("cod_barras"));
        prod.setNomeProduto(rs.getString("nome_produto"));
        prod.setPrecoProduto(rs.getBigDecimal("preco_produto"));

        prod.setEstoque(rs.getInt("estoque"));
        prod.setPermiteFiado(rs.getBoolean("permite_fiado"));
        prod.setTipoVenda(TipoVenda.valueOf(rs.getString("tipo_venda")));
        prod.setControlaEstoque(rs.getBoolean("controla_estoque"));
        prod.setPrecoCusto(rs.getBigDecimal("preco_custo"));
        prod.setAtivo(rs.getBoolean("ativo"));
        return prod;
    }

    @Override
    public Optional<Product> findByCodBarras(String codBarras) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM produto WHERE cod_barras = ?")) {
            st.setString(1, codBarras);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(instantiate(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
