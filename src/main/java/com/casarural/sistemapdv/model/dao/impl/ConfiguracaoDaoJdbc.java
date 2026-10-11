package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ConfiguracaoDaoJdbc implements ConfiguracaoDao {

    @Override
    public Optional<String> get(String chave) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT valor FROM configuracao WHERE chave = ?")) {
            st.setString(1, chave);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void set(String chave, String valor) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO configuracao (chave, valor) VALUES (?, ?) ON DUPLICATE KEY UPDATE valor = ?")) {
            st.setString(1, chave);
            st.setString(2, valor);
            st.setString(3, valor);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }
}
