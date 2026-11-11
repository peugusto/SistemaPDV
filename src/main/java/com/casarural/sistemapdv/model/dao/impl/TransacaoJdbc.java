package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

final class TransacaoJdbc {
    private static final Logger log = LoggerFactory.getLogger(TransacaoJdbc.class);

    @FunctionalInterface
    interface Operacao<T> {
        T executar(Connection conexao) throws SQLException;
    }

    private TransacaoJdbc() {}

    static <T> T executar(Supplier<Connection> conexoes, String descricao, Operacao<T> operacao) {
        Connection conexao = null;
        boolean iniciou = false;
        boolean encerrou = false;
        boolean iniciouCommit = false;
        try {
            conexao = conexoes.get();
            conexao.setAutoCommit(false);
            iniciou = true;
            T resultado = operacao.executar(conexao);
            iniciouCommit = true;
            conexao.commit();
            encerrou = true;
            return resultado;
        } catch (SQLException | RuntimeException e) {
            if (iniciou) {
                try {
                    conexao.rollback();
                    encerrou = true;
                } catch (SQLException | RuntimeException erroRollback) {
                    e.addSuppressed(erroRollback);
                }
            }
            if (iniciouCommit || (iniciou && !encerrou)) {
                throw new ResultadoTransacaoIncertoException(descricao, e);
            }
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new DbException("Não foi possível " + descricao + ". Tente novamente.", e);
        } finally {
            if (conexao != null) liberar(conexao, encerrou, descricao);
        }
    }

    private static void liberar(Connection conexao, boolean encerrou, String descricao) {
        try {
            if (encerrou) conexao.setAutoCommit(true);
            else conexao.abort(Runnable::run);
        } catch (SQLException | RuntimeException e) {
            log.warn("Falha ao restaurar ou descartar conexão após tentar {}", descricao, e);
        }
        try {
            conexao.close();
        } catch (SQLException | RuntimeException e) {
            log.warn("Falha ao liberar conexão após tentar {}", descricao, e);
        }
    }
}
