package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DAO real, banco transacional sintético: não resolve DB.config nem abre conexão externa. */
class ProductDaoJdbcEntradaTest {
    private static final int PRODUTO = 7;

    @Test
    void falhaAntesDeAtualizarDesfazLedgerEPermiteRepetirAMesmaEntrada() {
        Banco banco = new Banco(10);
        banco.falharAtualizacao = true;
        ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);
        EntradaEstoque entrada = entrada(UUID.randomUUID(), 5, "4.20");

        DbException erro = assertThrows(DbException.class, () -> dao.registrarEntrada(entrada));
        assertFalse(erro instanceof ResultadoTransacaoIncertoException);
        assertEquals(10, banco.estoque);
        assertTrue(banco.ledger.isEmpty(), "A operação não pode sobreviver ao rollback do aumento de estoque");
        assertEquals(1, banco.rollbacks);

        dao.registrarEntrada(entrada);
        assertEquals(15, banco.estoque);
        assertEquals(new BigDecimal("4.20"), banco.custo);
        assertEquals(1, banco.ledger.size());
        assertEquals(1, banco.atualizacoesConfirmadas);
    }

    @Test
    void commitAplicadoComRespostaPerdidaPodeSerReconciliadoPeloMesmoUuid() {
        Banco banco = new Banco(10);
        banco.perderRespostaCommit = true;
        ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);
        EntradaEstoque entrada = entrada(UUID.randomUUID(), 5, "4.20");

        assertThrows(ResultadoTransacaoIncertoException.class, () -> dao.registrarEntrada(entrada));
        assertEquals(15, banco.estoque, "O banco sintético aplicou o commit antes de perder a resposta");
        assertEquals(1, banco.ledger.size());

        dao.registrarEntrada(entrada);
        assertEquals(15, banco.estoque, "Reconciliar a mesma operação não acrescenta outras cinco unidades");
        assertEquals(1, banco.ledger.size());
        assertEquals(1, banco.atualizacoesConfirmadas);
    }

    @Test
    void entradaSomaAoSaldoAtualEDadoDeCustoAusentePreservaOCustoAtual() {
        Banco banco = new Banco(10);
        EntradaEstoque entrada = entrada(UUID.randomUUID(), 5, null);
        // A pesquisa da tela aconteceu com saldo 10; outra venda já deixou o banco com 9.
        banco.estoque = 9;

        new ProductDaoJdbc(banco::conectar).registrarEntrada(entrada);

        assertEquals(14, banco.estoque);
        assertEquals(new BigDecimal("2.50"), banco.custo);
        assertNull(banco.ledger.get(entrada.getIdOperacao().toString()).custo);
    }

    @Test
    void repetirEntradaAntigaNaoSomaNovamenteNemReverteCustoDeEntradaPosterior() {
        Banco banco = new Banco(10);
        ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);
        EntradaEstoque primeira = entrada(UUID.randomUUID(), 5, "4.20");

        dao.registrarEntrada(primeira);
        dao.registrarEntrada(primeira);
        dao.registrarEntrada(entrada(UUID.randomUUID(), 2, "6.70"));
        dao.registrarEntrada(primeira);

        assertEquals(17, banco.estoque);
        assertEquals(new BigDecimal("6.70"), banco.custo);
        assertEquals(2, banco.ledger.size());
        assertEquals(2, banco.atualizacoesConfirmadas);
    }

    @Test
    void ledgerHistoricoReconciliaMesmoAposExcluirProdutoESegueRejeitandoPayloadDivergente() {
        Banco banco = new Banco(10);
        ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);
        EntradaEstoque entrada = entrada(UUID.randomUUID(), 5, null);
        dao.registrarEntrada(entrada);
        banco.existe = false;

        assertDoesNotThrow(() -> dao.registrarEntrada(entrada));
        assertThrows(DbException.class, () -> dao.registrarEntrada(
                new EntradaEstoque(entrada.getIdOperacao(), PRODUTO + 1, 5, null)));

        assertEquals(1, banco.ledger.size());
        assertEquals(1, banco.atualizacoesConfirmadas);
        // A ausência de FK/cascade precisa ser mantida pela migração; esta fixture só modela o DAO.
    }

    @Test
    void mesmoUuidComProdutoQuantidadeOuCustoDiferentesNaoAlteraOperacaoConfirmada() {
        Banco banco = new Banco(10);
        ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);
        UUID id = UUID.randomUUID();
        dao.registrarEntrada(entrada(id, 5, "4.20"));

        EntradaEstoque[] divergentes = {
                new EntradaEstoque(id, PRODUTO + 1, 5, new BigDecimal("4.20")),
                entrada(id, 6, "4.20"), entrada(id, 5, "4.21"), entrada(id, 5, null)
        };
        for (EntradaEstoque divergente : divergentes) {
            DbException erro = assertThrows(DbException.class, () -> dao.registrarEntrada(divergente));
            assertFalse(erro instanceof ResultadoTransacaoIncertoException);
            assertEquals(15, banco.estoque);
            assertEquals(new BigDecimal("4.20"), banco.custo);
            assertEquals(1, banco.ledger.size());
            assertEquals(1, banco.atualizacoesConfirmadas);
        }
    }

    @Test
    void overflowProdutoInativoSemControleSaldoNegativoOuProdutoAusenteNaoDeixamLedger() {
        for (int caso = 0; caso < 5; caso++) {
            Banco banco = new Banco(caso == 0 ? Integer.MAX_VALUE : caso == 3 ? -1 : 10);
            banco.ativo = caso != 1;
            banco.controlaEstoque = caso != 2;
            banco.existe = caso != 4;
            ProductDaoJdbc dao = new ProductDaoJdbc(banco::conectar);

            DbException erro = assertThrows(DbException.class,
                    () -> dao.registrarEntrada(entrada(UUID.randomUUID(), 5, "4.20")));

            assertFalse(erro instanceof ResultadoTransacaoIncertoException);
            assertEquals(caso == 0 ? Integer.MAX_VALUE : caso == 3 ? -1 : 10, banco.estoque);
            assertEquals(new BigDecimal("2.50"), banco.custo);
            assertTrue(banco.ledger.isEmpty(), "Ledger não confirmado no caso " + caso);
            assertEquals(0, banco.atualizacoesConfirmadas);
        }
    }

    @Test
    void rollbackQueFalhaSinalizaIncertezaSemConfirmarPendenciasPorAutocommit() {
        for (boolean falhaRuntime : new boolean[]{false, true}) {
            Banco banco = new Banco(10);
            banco.falharAtualizacao = true;
            banco.falharRollback = true;
            banco.rollbackRuntime = falhaRuntime;

            assertThrows(ResultadoTransacaoIncertoException.class,
                    () -> new ProductDaoJdbc(banco::conectar).registrarEntrada(entrada(UUID.randomUUID(), 5, null)));

            assertEquals(1, banco.aborts, "A conexão pendente deve ser descartada antes de voltar ao pool");
            assertEquals(0, banco.autocommitsComPendencias);
            assertEquals(0, banco.atualizacoesConfirmadas);
            // A fixture não prova qual seria o resultado no servidor após uma falha real de rollback.
        }
    }

    @Test
    void falhaAoLiberarConexaoDepoisDoCommitNaoAnunciaEntradaComoNaoGravada() {
        for (boolean falhaRuntime : new boolean[]{false, true}) {
            Banco banco = new Banco(10);
            banco.falharClose = true;
            banco.closeRuntime = falhaRuntime;
            EntradaEstoque entrada = entrada(UUID.randomUUID(), 5, null);

            assertDoesNotThrow(() -> new ProductDaoJdbc(banco::conectar).registrarEntrada(entrada));

            assertEquals(15, banco.estoque);
            assertEquals(1, banco.ledger.size());
            assertEquals(1, banco.atualizacoesConfirmadas);
        }
    }

    private static EntradaEstoque entrada(UUID id, int quantidade, String custo) {
        return new EntradaEstoque(id, PRODUTO, quantidade, custo == null ? null : new BigDecimal(custo));
    }

    private static final class Operacao {
        final int produto, quantidade;
        final BigDecimal custo;
        Operacao(int produto, int quantidade, BigDecimal custo) {
            this.produto = produto;
            this.quantidade = quantidade;
            this.custo = custo;
        }
    }

    /** Apenas este estado representa dados confirmados; cada conexão tem uma cópia pendente. */
    private static final class Banco {
        int estoque, rollbacks, aborts, atualizacoesConfirmadas, autocommitsComPendencias;
        BigDecimal custo = new BigDecimal("2.50");
        boolean ativo = true, controlaEstoque = true, existe = true;
        boolean falharAtualizacao, perderRespostaCommit, falharRollback, falharClose;
        boolean rollbackRuntime, closeRuntime;
        Map<String, Operacao> ledger = new LinkedHashMap<>();

        Banco(int estoque) { this.estoque = estoque; }

        Connection conectar() {
            return new Transacao(this).conexao();
        }
    }

    private static final class Transacao {
        final Banco banco;
        int estoque, atualizacoes;
        BigDecimal custo;
        Map<String, Operacao> ledger;
        boolean pendente;

        Transacao(Banco banco) { this.banco = banco; restaurar(); }

        void restaurar() {
            estoque = banco.estoque;
            custo = banco.custo;
            ledger = new LinkedHashMap<>(banco.ledger);
            atualizacoes = 0;
            pendente = false;
        }

        void confirmar() {
            banco.estoque = estoque;
            banco.custo = custo;
            banco.ledger = new LinkedHashMap<>(ledger);
            banco.atualizacoesConfirmadas += atualizacoes;
            atualizacoes = 0;
            pendente = false;
        }

        Connection conexao() {
            return proxy(Connection.class, (obj, metodo, args) -> {
                switch (metodo.getName()) {
                    case "setAutoCommit":
                        if (Boolean.TRUE.equals(args[0]) && pendente) {
                            banco.autocommitsComPendencias++;
                            confirmar(); // Contrato JDBC: mudar para autocommit confirma transação pendente.
                        }
                        return null;
                    case "prepareStatement": return statement((String) args[0]);
                    case "commit":
                        confirmar();
                        if (banco.perderRespostaCommit) {
                            banco.perderRespostaCommit = false;
                            throw new SQLException("Resposta do commit perdida depois da aplicação", "08007");
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
                    case "abort": banco.aborts++; pendente = false; return null;
                    case "close":
                        if (banco.falharClose) {
                            if (banco.closeRuntime) throw new IllegalStateException("Conexão já confirmada falhou ao fechar");
                            throw new SQLException("Conexão já confirmada falhou ao fechar", "08006");
                        }
                        return null;
                    default: throw new AssertionError("Método JDBC não modelado: " + metodo.getName());
                }
            });
        }

        PreparedStatement statement(String sqlOriginal) {
            String sql = sqlOriginal.replaceAll("\\s+", " ").trim();
            Map<Integer, Object> p = new HashMap<>();
            return proxy(PreparedStatement.class, (obj, metodo, args) -> {
                String nome = metodo.getName();
                if (nome.startsWith("set")) { p.put((Integer) args[0], nome.equals("setNull") ? null : args[1]); return null; }
                if (nome.equals("close")) return null;
                if (nome.equals("executeUpdate") && sql.startsWith("INSERT INTO entrada_estoque")) {
                    String id = (String) p.get(1);
                    if (ledger.containsKey(id)) throw new SQLException("Operação repetida", "23000", 1062);
                    ledger.put(id, new Operacao((Integer) p.get(2), (Integer) p.get(3), (BigDecimal) p.get(4)));
                    pendente = true;
                    return 1;
                }
                if (nome.equals("executeQuery") && sql.startsWith("SELECT id_produto, quantidade, custo_informado FROM entrada_estoque")) {
                    assertTrue(sql.endsWith("FOR UPDATE"), "Leitura da chave repetida deve ser corrente");
                    return resultado(ledger.get((String) p.get(1)));
                }
                if (nome.equals("executeUpdate") && sql.startsWith("UPDATE produto")) {
                    // Não fingir que um UPDATE absoluto/sem condições tem a semântica de incremento protegido.
                    assertTrue(sql.contains("estoque = estoque + ?"));
                    assertTrue(sql.contains("preco_custo = COALESCE(?, preco_custo)"));
                    assertTrue(sql.contains("WHERE id_produto = ? AND ativo AND controla_estoque"));
                    assertTrue(sql.contains("AND estoque >= 0 AND estoque <= ?"));
                    if (banco.falharAtualizacao) {
                        banco.falharAtualizacao = false;
                        throw new SQLException("Falha antes do aumento de estoque", "HY000");
                    }
                    int quantidade = (Integer) p.get(1);
                    int limite = (Integer) p.get(4);
                    assertEquals(Integer.MAX_VALUE - quantidade, limite);
                    if (!banco.existe || (Integer) p.get(3) != PRODUTO || !banco.ativo || !banco.controlaEstoque
                            || estoque < 0 || estoque > limite) return 0;
                    estoque += quantidade;
                    if (p.get(2) != null) custo = (BigDecimal) p.get(2);
                    atualizacoes++;
                    pendente = true;
                    return 1;
                }
                throw new AssertionError("SQL não modelado: " + nome + ": " + sql);
            });
        }
    }

    private static ResultSet resultado(Operacao operacao) {
        boolean[] lida = {false};
        return proxy(ResultSet.class, (obj, metodo, args) -> {
            switch (metodo.getName()) {
                case "next": boolean existe = operacao != null && !lida[0]; lida[0] = true; return existe;
                case "getInt":
                    if (args[0].equals("id_produto")) return operacao.produto;
                    if (args[0].equals("quantidade")) return operacao.quantidade;
                    throw new AssertionError("Coluna inesperada: " + args[0]);
                case "getBigDecimal": assertEquals("custo_informado", args[0]); return operacao.custo;
                case "close": return null;
                default: throw new AssertionError("ResultSet: " + metodo.getName());
            }
        });
    }

    private static <T> T proxy(Class<T> tipo, InvocationHandler handler) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler));
    }
}
