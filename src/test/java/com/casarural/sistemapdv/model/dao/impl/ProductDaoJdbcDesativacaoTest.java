package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercita o DAO real com estado confirmado separado de alterações transacionais, sem banco externo. */
class ProductDaoJdbcDesativacaoTest {
    private static final int PRODUTO = 17;

    @Test
    void desativaSomenteAtivoSemRegravarEstoqueCustoOuOutrosMetadados() {
        Banco banco = new Banco();
        Map<String, Object> camposAntes = Map.copyOf(banco.campos);

        new ProductDaoJdbc(banco::conectar).desativarPorId(PRODUTO);

        assertFalse(banco.ativo);
        assertEquals(camposAntes, banco.campos);
        assertEquals(1, banco.desativacoesConfirmadas);
        assertEquals(1, banco.commits);
        assertEquals(0, banco.rollbacks);
        assertEquals(List.of("UPDATE produto SET ativo = FALSE WHERE id_produto = ?"), banco.sqlExecutado);
    }

    @Test
    void falhaAntesDeConfirmarPreservaEstadoEPermiteRepetirDesativacao() {
        Banco banco = new Banco();
        banco.falharDepoisDoUpdate = true;
        ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);

        DbException erro = assertThrows(DbException.class, () -> dao.desativarPorId(PRODUTO));
        assertFalse(erro instanceof ResultadoTransacaoIncertoException);
        assertTrue(banco.ativo);
        assertEquals(0, banco.desativacoesConfirmadas);
        assertEquals(1, banco.rollbacks);

        dao.desativarPorId(PRODUTO);
        assertFalse(banco.ativo);
        assertEquals(1, banco.desativacoesConfirmadas);
    }

    @Test
    void commitSemRespostaExigeConferenciaERepeticaoConvergeSemAlterarOutrosCampos() {
        for (boolean servidorConfirmou : new boolean[]{false, true}) {
            Banco banco = new Banco();
            banco.falharCommit = true;
            banco.aplicarAntesDaFalhaCommit = servidorConfirmou;
            ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);

            assertThrows(ResultadoTransacaoIncertoException.class, () -> dao.desativarPorId(PRODUTO));
            assertEquals(!servidorConfirmou, banco.ativo);

            dao.desativarPorId(PRODUTO);
            assertFalse(banco.ativo);
            assertEquals(1, banco.desativacoesConfirmadas);
            assertEquals(9, banco.campos.get("estoque"));
            assertEquals(new BigDecimal("7.30"), banco.campos.get("preco_custo"));
        }
    }

    @Test
    void zeroLinhasDistingueProdutoJaInativoDeProdutoInexistente() {
        Banco existente = new Banco();
        existente.ativo = false; // Simula driver que informa apenas linhas efetivamente modificadas.
        assertDoesNotThrow(() -> new ProductDaoJdbc(existente::conectar).desativarPorId(PRODUTO));
        assertFalse(existente.ativo);
        assertEquals(1, existente.consultasExistencia);
        assertEquals(1, existente.commits);
        assertEquals(0, existente.desativacoesConfirmadas);

        Banco ausente = new Banco();
        ausente.existe = false;
        DbException erro = assertThrows(DbException.class,
                () -> new ProductDaoJdbc(ausente::conectar).desativarPorId(PRODUTO));
        assertFalse(erro instanceof ResultadoTransacaoIncertoException);
        assertTrue(erro.getMessage().contains("não encontrado"));
        assertEquals(1, ausente.consultasExistencia);
        assertEquals(0, ausente.commits);
        assertEquals(1, ausente.rollbacks);
    }

    @Test
    void falhaSqlOuRuntimeDeCloseAposCommitNaoTransformaSucessoEmErro() {
        for (boolean falhaRuntime : new boolean[]{false, true}) {
            Banco banco = new Banco();
            banco.falharClose = true;
            banco.closeRuntime = falhaRuntime;

            assertDoesNotThrow(() -> new ProductDaoJdbc(banco::conectar).desativarPorId(PRODUTO));

            assertFalse(banco.ativo);
            assertEquals(1, banco.commits);
            assertEquals(1, banco.desativacoesConfirmadas);
            assertEquals(0, banco.rollbacks);
            assertEquals(1, banco.closes);
        }
    }

    @Test
    void rollbackSqlOuRuntimeFalhoDescartaConexaoSemConfirmarPendenciasPorAutocommit() {
        for (boolean falhaRuntime : new boolean[]{false, true}) {
            Banco banco = new Banco();
            banco.falharDepoisDoUpdate = true;
            banco.falharRollback = true;
            banco.rollbackRuntime = falhaRuntime;

            assertThrows(ResultadoTransacaoIncertoException.class,
                    () -> new ProductDaoJdbc(banco::conectar).desativarPorId(PRODUTO));

            assertEquals(1, banco.aborts);
            assertEquals(1, banco.closes);
            assertEquals(0, banco.autocommitsComPendencias);
            assertEquals(0, banco.commits);
            assertTrue(banco.eventos.indexOf("abort") < banco.eventos.indexOf("close"));
            // O descarte foi solicitado; a fixture não prova o resultado de uma falha de conexão real.
        }
    }

    @Test
    void identificadorInvalidoNaoObtemConexao() {
        ProductDaoJdbc dao = new ProductDaoJdbc(() -> { throw new AssertionError("Conexão indevida"); });
        assertThrows(DbException.class, () -> dao.desativarPorId(0));
        assertThrows(DbException.class, () -> dao.desativarPorId(-1));
    }

    private static final class Banco {
        boolean ativo = true, existe = true;
        boolean falharDepoisDoUpdate, falharCommit, aplicarAntesDaFalhaCommit;
        boolean falharRollback, rollbackRuntime, falharClose, closeRuntime;
        int commits, rollbacks, aborts, closes, desativacoesConfirmadas, consultasExistencia, autocommitsComPendencias;
        final Map<String, Object> campos = Map.of(
                "cod_barras", "78917", "nome_produto", "Produto atual", "preco_produto", new BigDecimal("12.40"),
                "estoque", 9, "preco_custo", new BigDecimal("7.30"), "permite_fiado", false,
                "tipo_venda", "UNIDADE", "controla_estoque", true);
        final List<String> sqlExecutado = new ArrayList<>();
        final List<String> eventos = new ArrayList<>();

        Connection conectar() { return new Transacao(this).conexao(); }
    }

    private static final class Transacao {
        final Banco banco;
        boolean ativo, pendente;
        int alteracoes;

        Transacao(Banco banco) { this.banco = banco; restaurar(); }

        void restaurar() { ativo = banco.ativo; pendente = false; alteracoes = 0; }
        void confirmar() {
            banco.ativo = ativo;
            banco.desativacoesConfirmadas += alteracoes;
            alteracoes = 0;
            pendente = false;
        }

        Connection conexao() {
            return proxy(Connection.class, (obj, metodo, args) -> {
                switch (metodo.getName()) {
                    case "setAutoCommit":
                        if (Boolean.TRUE.equals(args[0]) && pendente) {
                            banco.autocommitsComPendencias++;
                            confirmar();
                        }
                        return null;
                    case "prepareStatement": return statement((String) args[0]);
                    case "commit":
                        banco.commits++;
                        if (!banco.falharCommit || banco.aplicarAntesDaFalhaCommit) confirmar();
                        if (banco.falharCommit) {
                            banco.falharCommit = false;
                            throw new SQLException("Resposta do commit indisponível", "08007");
                        }
                        return null;
                    case "rollback":
                        banco.rollbacks++;
                        if (banco.falharRollback) {
                            if (banco.rollbackRuntime) throw new IllegalStateException("Rollback não confirmado");
                            throw new SQLException("Rollback não confirmado", "08007");
                        }
                        restaurar();
                        return null;
                    case "abort": banco.eventos.add("abort"); banco.aborts++; pendente = false; return null;
                    case "close":
                        banco.eventos.add("close"); banco.closes++;
                        if (banco.falharClose) {
                            if (banco.closeRuntime) throw new IllegalStateException("Falha ao liberar conexão");
                            throw new SQLException("Falha ao liberar conexão", "08006");
                        }
                        return null;
                    default: throw new AssertionError("Connection não modelada: " + metodo.getName());
                }
            });
        }

        PreparedStatement statement(String sqlOriginal) {
            String sql = sqlOriginal.replaceAll("\\s+", " ").trim();
            int[] id = {0};
            return proxy(PreparedStatement.class, (obj, metodo, args) -> {
                switch (metodo.getName()) {
                    case "setInt": assertEquals(1, args[0]); id[0] = (Integer) args[1]; return null;
                    case "close": return null;
                    case "executeUpdate":
                        assertEquals("UPDATE produto SET ativo = FALSE WHERE id_produto = ?", sql,
                                "Desativar não pode reenviar o objeto antigo da tela");
                        banco.sqlExecutado.add(sql);
                        if (!banco.existe || id[0] != PRODUTO) return 0;
                        int linhas = ativo ? 1 : 0;
                        if (ativo) { ativo = false; alteracoes++; pendente = true; }
                        if (banco.falharDepoisDoUpdate) {
                            banco.falharDepoisDoUpdate = false;
                            throw new SQLException("Falha antes do commit", "HY000");
                        }
                        return linhas;
                    case "executeQuery":
                        assertEquals("SELECT id_produto FROM produto WHERE id_produto = ? FOR UPDATE", sql);
                        banco.sqlExecutado.add(sql);
                        banco.consultasExistencia++;
                        return resultado(banco.existe && id[0] == PRODUTO);
                    default: throw new AssertionError("PreparedStatement não modelada: " + metodo.getName());
                }
            });
        }
    }

    private static ResultSet resultado(boolean existe) {
        boolean[] lido = {false};
        return proxy(ResultSet.class, (obj, metodo, args) -> {
            switch (metodo.getName()) {
                case "next": boolean linha = existe && !lido[0]; lido[0] = true; return linha;
                case "close": return null;
                default: throw new AssertionError("ResultSet não modelado: " + metodo.getName());
            }
        });
    }

    private static <T> T proxy(Class<T> tipo, InvocationHandler handler) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler));
    }
}
