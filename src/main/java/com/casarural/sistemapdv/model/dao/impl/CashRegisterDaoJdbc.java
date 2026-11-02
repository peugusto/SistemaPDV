package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
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
    private final java.util.function.Supplier<Connection> conexoes;

    public CashRegisterDaoJdbc() { this(DB::getConnection); }
    CashRegisterDaoJdbc(java.util.function.Supplier<Connection> conexoes) { this.conexoes = conexoes; }

    @FunctionalInterface
    private interface Operacao<T> { T executar(Connection c) throws SQLException; }

    private <T> T transacao(String descricao, Operacao<T> operacao) {
        Connection c = null;
        boolean iniciada = false, encerrada = false, commitIniciado = false;
        try {
            c = conexoes.get();
            c.setAutoCommit(false);
            iniciada = true;
            T resultado = operacao.executar(c);
            commitIniciado = true;
            c.commit();
            encerrada = true;
            return resultado;
        } catch (SQLException | RuntimeException e) {
            if (iniciada) {
                try { c.rollback(); encerrada = true; }
                catch (SQLException | RuntimeException rollback) { e.addSuppressed(rollback); }
            }
            if (commitIniciado || (iniciada && !encerrada)) throw new ResultadoTransacaoIncertoException(descricao, e);
            if (e instanceof SQLException) throw new DbException(e.getMessage(), e);
            throw (RuntimeException) e;
        } finally {
            if (c != null) {
                // Limpeza posterior ao commit não transforma sucesso em convite para repetir a operação.
                try { if (encerrada) c.setAutoCommit(true); else c.abort(Runnable::run); }
                catch (SQLException | RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Falha ao liberar conexão de {}", descricao, e); }
                try { c.close(); }
                catch (SQLException | RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Falha ao fechar conexão de {}", descricao, e); }
            }
        }
    }

    private CashRegister bloquearAberto(Connection conn, int id) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement("SELECT * FROM caixa WHERE id_caixa = ? FOR UPDATE")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) throw new DbException("Caixa não encontrado.");
                CashRegister caixa = instantiate(rs);
                if (!caixa.isAberto()) throw new DbException("Este caixa já estava fechado. Atualize a tela.");
                return caixa;
            }
        }
    }

    @Override
    public Optional<CashRegister> findOpen() {
        try (Connection conn = conexoes.get();
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
        try (Connection conn = conexoes.get();
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
    public CashRegister close(int idCaixa, BigDecimal valorContado, String observacao) {
        return transacao("fechar o caixa #" + idCaixa, conn -> {
            CashRegister caixa = bloquearAberto(conn, idCaixa);
            BigDecimal valorEsperado = summarize(conn, caixa).getDinheiroEsperado();
            LocalDateTime fechamento = LocalDateTime.now().withNano(0);
            try (PreparedStatement st = conn.prepareStatement(
                     "UPDATE caixa SET fechado_em = ?, valor_esperado = ?, valor_contado = ?, observacao = ? " +
                             "WHERE id_caixa = ? AND fechado_em IS NULL")) {
                st.setTimestamp(1, Timestamp.valueOf(fechamento));
                st.setBigDecimal(2, valorEsperado);
                if (valorContado != null) st.setBigDecimal(3, valorContado); else st.setNull(3, Types.DECIMAL);
                st.setString(4, observacao);
                st.setInt(5, idCaixa);
                if (st.executeUpdate() == 0) throw new DbException("Este caixa já estava fechado.");
            }
            caixa.setFechadoEm(fechamento);
            caixa.setValorEsperado(valorEsperado);
            caixa.setValorContado(valorContado);
            caixa.setObservacao(observacao);
            return caixa;
        });
    }

    @Override
    public void addMovement(int idCaixa, CashMovementType tipo, BigDecimal valor, String motivo) {
        if (tipo == null || !com.casarural.sistemapdv.util.Money.isPositive(valor)) {
            throw new DbException("Informe um movimento válido com valor maior que zero.");
        }
        BigDecimal normalizado = com.casarural.sistemapdv.util.Money.of(valor);
        if (normalizado.signum() <= 0) throw new DbException("Informe um valor de pelo menos R$ 0,01.");
        transacao("registrar movimento no caixa #" + idCaixa, conn -> {
            CashRegister caixa = bloquearAberto(conn, idCaixa);
            if (tipo == CashMovementType.SANGRIA) {
                BigDecimal disponivel = summarize(conn, caixa).getDinheiroEsperado();
                if (normalizado.compareTo(disponivel) > 0) {
                    throw new DbException(String.format(
                            "Pela conta do sistema há %s em dinheiro na gaveta. Não é possível retirar %s.",
                            com.casarural.sistemapdv.util.Money.format(disponivel), com.casarural.sistemapdv.util.Money.format(normalizado)));
                }
            }
            try (PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO movimento_caixa (id_caixa, tipo, valor, motivo) VALUES (?, ?, ?, ?)")) {
                st.setInt(1, idCaixa);
                st.setString(2, tipo.toString());
                st.setBigDecimal(3, normalizado);
                st.setString(4, motivo);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<CashMovement> findMovements(int idCaixa) {
        try (Connection conn = conexoes.get();
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
        try (Connection conn = conexoes.get()) {
            return summarize(conn, caixa);
        } catch (SQLException e) {
            throw new DbException(e.getMessage(), e);
        }
    }

    CashSummary summarize(Connection conn, CashRegister caixa) throws SQLException {
        CashSummary resumo = new CashSummary(caixa.getValorInicial());
        try (PreparedStatement st = conn.prepareStatement(
                "SELECT origem, metodo_pagamento, SUM(valor_pago) AS total FROM pagamento_caixa " +
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
        DevolucaoVendaJdbc.compensar(conn,caixa.getIdCaixa(),resumo);
        return resumo;
    }

    @Override
    public List<CashRegister> findClosed(int limite) {
        try (Connection conn = conexoes.get();
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
