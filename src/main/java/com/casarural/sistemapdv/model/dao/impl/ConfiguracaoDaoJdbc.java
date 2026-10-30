package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ConfiguracaoDaoJdbc implements ConfiguracaoDao {

    private final Supplier<Connection> conexoes;

    public ConfiguracaoDaoJdbc() { this(DB::getConnection); }

    public ConfiguracaoDaoJdbc(Supplier<Connection> conexoes) {
        this.conexoes = Objects.requireNonNull(conexoes);
    }

    @Override
    public Optional<String> get(String chave) {
        try (Connection conn = conexoes.get();
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
        try (Connection conn = conexoes.get();
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

    @Override
    public String atualizar(String chave, String inicial, UnaryOperator<String> alteracao) {
        try (Connection conn = conexoes.get()) {
            conn.setAutoCommit(false);
            try {
                // O upsert também bloqueia a linha na primeira tentativa concorrente.
                try (PreparedStatement st = conn.prepareStatement(
                        "INSERT INTO configuracao (chave, valor) VALUES (?, ?) ON DUPLICATE KEY UPDATE chave = chave")) {
                    st.setString(1, chave);
                    st.setString(2, inicial);
                    st.executeUpdate();
                }
                String atual;
                try (PreparedStatement st = conn.prepareStatement("SELECT valor FROM configuracao WHERE chave = ? FOR UPDATE")) {
                    st.setString(1, chave);
                    try (ResultSet rs = st.executeQuery()) {
                        if (!rs.next()) throw new DbException("Configuração não encontrada.");
                        atual = rs.getString(1);
                    }
                }
                String novo = Objects.requireNonNull(alteracao.apply(atual));
                try (PreparedStatement st = conn.prepareStatement("UPDATE configuracao SET valor = ? WHERE chave = ?")) {
                    st.setString(1, novo);
                    st.setString(2, chave);
                    st.executeUpdate();
                }
                conn.commit();
                return novo;
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException erro) { e.addSuppressed(erro); }
                throw e;
            }
        } catch (SQLException e) {
            throw new DbException("Não foi possível registrar a tentativa de PIN. Tente novamente.", e);
        }
    }
}
