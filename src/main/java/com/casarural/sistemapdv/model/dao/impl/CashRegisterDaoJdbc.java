package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CashRegisterDao;
import com.casarural.sistemapdv.model.entities.CashMovement;
import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.model.entities.CashSummary;
import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CashRegisterDaoJdbc implements CashRegisterDao {

    @Override
    public Optional<CashRegister> findOpen() {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement("SELECT * FROM caixa WHERE fechado_em IS NULL");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? Optional.of(instantiate(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public CashRegister open(BigDecimal valorInicial) {
        LocalDateTime agora = LocalDateTime.now().withNano(0);
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO caixa (aberto_em, valor_inicial) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            st.setTimestamp(1, Timestamp.valueOf(agora));
            st.setBigDecimal(2, valorInicial);
            st.executeUpdate();

            CashRegister caixa = new CashRegister();
            try (ResultSet rs = st.getGeneratedKeys()) {
                rs.next();
                caixa.setIdCaixa(rs.getInt(1));
            }
            caixa.setAbertoEm(agora);
            caixa.setValorInicial(valorInicial);
            return caixa;
        } catch (SQLIntegrityConstraintViolationException e) {
            // índice único uk_um_caixa_aberto: já existe um caixa aberto
            throw new DbException("Já existe um caixa aberto. Feche o caixa atual antes de abrir outro.", e);
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void close(int idCaixa, BigDecimal valorEsperado, BigDecimal valorContado, String observacao) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE caixa SET fechado_em = ?, valor_esperado = ?, valor_contado = ?, observacao = ? " +
                             "WHERE id_caixa = ? AND fechado_em IS NULL")) {
            st.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().withNano(0)));
            st.setBigDecimal(2, valorEsperado);
            if (valorContado != null) st.setBigDecimal(3, valorContado); else st.setNull(3, Types.DECIMAL);
            st.setString(4, observacao);
            st.setInt(5, idCaixa);
            if (st.executeUpdate() == 0) {
                throw new DbException("Este caixa já estava fechado.");
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public void addMovement(int idCaixa, CashMovementType tipo, BigDecimal valor, String motivo) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO movimento_caixa (id_caixa, tipo, valor, motivo) VALUES (?, ?, ?, ?)")) {
            st.setInt(1, idCaixa);
            st.setString(2, tipo.toString());
            st.setBigDecimal(3, valor);
            st.setString(4, motivo);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public List<CashMovement> findMovements(int idCaixa) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT * FROM movimento_caixa WHERE id_caixa = ? ORDER BY data_movimento")) {
            st.setInt(1, idCaixa);
            try (ResultSet rs = st.executeQuery()) {
                List<CashMovement> list = new ArrayList<>();
                while (rs.next()) {
                    CashMovement m = new CashMovement();
                    m.setIdMovimento(rs.getInt("id_movimento"));
                    m.setIdCaixa(rs.getInt("id_caixa"));
                    m.setTipo(CashMovementType.valueOf(rs.getString("tipo")));
                    m.setValor(rs.getBigDecimal("valor"));
                    m.setMotivo(rs.getString("motivo"));
                    m.setDataMovimento(rs.getTimestamp("data_movimento").toLocalDateTime());
                    list.add(m);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public CashSummary summarize(CashRegister caixa) {
        CashSummary resumo = new CashSummary(caixa.getValorInicial());
        try (Connection conn = DB.getConnection()) {
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT origem, metodo_pagamento, SUM(valor_pago) AS total FROM pagamento " +
                            "WHERE id_caixa = ? GROUP BY origem, metodo_pagamento")) {
                st.setInt(1, caixa.getIdCaixa());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        resumo.addPagamento(PaymentOrigin.valueOf(rs.getString("origem")),
                                PaymentMethod.valueOf(rs.getString("metodo_pagamento")),
                                rs.getBigDecimal("total"));
                    }
                }
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT tipo, SUM(valor) AS total FROM movimento_caixa WHERE id_caixa = ? GROUP BY tipo")) {
                st.setInt(1, caixa.getIdCaixa());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        resumo.addMovimento(CashMovementType.valueOf(rs.getString("tipo")), rs.getBigDecimal("total"));
                    }
                }
            }
            return resumo;
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    @Override
    public List<CashRegister> findClosed(int limite) {
        try (Connection conn = DB.getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT * FROM caixa WHERE fechado_em IS NOT NULL ORDER BY aberto_em DESC LIMIT ?")) {
            st.setInt(1, limite);
            try (ResultSet rs = st.executeQuery()) {
                List<CashRegister> list = new ArrayList<>();
                while (rs.next()) list.add(instantiate(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    private CashRegister instantiate(ResultSet rs) throws SQLException {
        CashRegister c = new CashRegister();
        c.setIdCaixa(rs.getInt("id_caixa"));
        c.setAbertoEm(rs.getTimestamp("aberto_em").toLocalDateTime());
        c.setValorInicial(rs.getBigDecimal("valor_inicial"));
        Timestamp fechado = rs.getTimestamp("fechado_em");
        c.setFechadoEm(fechado == null ? null : fechado.toLocalDateTime());
        c.setValorEsperado(rs.getBigDecimal("valor_esperado"));
        c.setValorContado(rs.getBigDecimal("valor_contado"));
        c.setObservacao(rs.getString("observacao"));
        return c;
    }
}
