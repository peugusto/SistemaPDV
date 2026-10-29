package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Falhas de JDBC simuladas; não carrega configuração nem abre conexão real. */
class OrderDaoJdbcTransacaoTest {
    private static final LocalDateTime DATA = LocalDateTime.of(2026, 10, 29, 12, 0);
    private static final BigDecimal DEZ = Money.of(new BigDecimal("10.00"));

    private enum Fluxo {
        VENDA, CANCELAMENTO, ESTORNO;

        Object executar(Banco banco) {
            OrderDaoJdbc dao = new OrderDaoJdbc(banco::conexao);
            if (this == CANCELAMENTO) return dao.cancel(401, "Teste isolado");
            if (this == ESTORNO) return dao.reverseFiadoReceipt(7, DATA, PaymentMethod.DINHEIRO, "Teste isolado");
            Product produto = new Product("TESTE", "TESTE", DEZ, 20);
            produto.setIdProduto(3);
            Order venda = new Order();
            venda.setStatus(OrderStatus.PAGO);
            venda.setItemPedido(new ArrayList<>(List.of(new OrderItem(produto, 1, DEZ))));
            venda.aplicarDesconto(Money.ZERO);
            venda.getPagamentos().add(new Order.PaymentSlice(DEZ, PaymentMethod.DINHEIRO));
            dao.insert(venda);
            return venda.getIdPedido();
        }

        void conferirResultado(Object resultado) {
            if (this == VENDA) assertEquals(401, resultado);
            else if (this == CANCELAMENTO) assertEquals(Map.of(PaymentMethod.DINHEIRO, DEZ), resultado);
            else assertEquals(DEZ, resultado);
        }
    }

    @TestFactory
    Stream<DynamicTest> resultadoConfirmadoSobreviveAFalhasDeLimpeza() {
        return Stream.of(Fluxo.values()).flatMap(fluxo -> Stream.of("autocommit", "close", "ambos").map(falha ->
                DynamicTest.dynamicTest(fluxo + " confirmado com falha em " + falha, () -> {
                    Banco banco = new Banco();
                    banco.falhaReset = !falha.equals("close");
                    banco.falhaClose = !falha.equals("autocommit");
                    fluxo.conferirResultado(fluxo.executar(banco));
                    assertEquals(1, banco.commits);
                    assertFalse(banco.confirmados.isEmpty());
                    assertTrue(banco.pendentes.isEmpty());
                    assertEquals(0, banco.rollbacks, "Limpeza não pode desfazer nem repetir a operação confirmada");
                    assertEquals(0, banco.commitsImplicitos);
                    assertEquals(1, banco.closes);
                })));
    }

    @TestFactory
    Stream<DynamicTest> erroAntesDaPrimeiraEscritaNaoConfirmaNada() {
        return Stream.of(Fluxo.values()).map(fluxo -> DynamicTest.dynamicTest(fluxo + " falha antes da escrita", () -> {
            Banco banco = new Banco(); banco.falhaConsulta = true;
            DbException erro = assertThrows(DbException.class, () -> fluxo.executar(banco));
            assertFalse(erro instanceof ResultadoTransacaoIncertoException);
            assertEquals("CONSULTA FALHOU", erro.getMessage());
            assertEquals(0, banco.escritas);
            assertEquals(0, banco.commits);
            assertEquals(1, banco.rollbacks);
            assertEquals(1, banco.closes);
        }));
    }

    @TestFactory
    Stream<DynamicTest> erroNaEscritaDesfazTodasAsMudancas() {
        return Stream.of(Fluxo.values()).map(fluxo -> DynamicTest.dynamicTest(fluxo + " rollback após escrita", () -> {
            Banco banco = new Banco(); banco.falhaNaEscrita = 2;
            DbException erro = assertThrows(DbException.class, () -> fluxo.executar(banco));
            assertSame(banco.erroEscrita, erro.getCause());
            assertFalse(erro instanceof ResultadoTransacaoIncertoException);
            assertEquals(1, banco.rollbacks);
            assertEquals(0, banco.commits);
            assertTrue(banco.pendentes.isEmpty());
            assertTrue(banco.confirmados.isEmpty());
            assertEquals(0, banco.commitsImplicitos);
        }));
    }

    @TestFactory
    Stream<DynamicTest> rollbackFalhoNaoReligaAutoCommitEDescartaConexao() {
        return Stream.of(Fluxo.values()).map(fluxo -> DynamicTest.dynamicTest(fluxo + " rollback falho", () -> {
            Banco banco = new Banco(); banco.falhaNaEscrita = 2; banco.falhaRollback = true;
            ResultadoTransacaoIncertoException erro = assertThrows(ResultadoTransacaoIncertoException.class,
                    () -> fluxo.executar(banco));
            assertSame(banco.erroEscrita, erro.getCause());
            assertEquals(1, erro.getCause().getSuppressed().length);
            assertEquals("ROLLBACK FALHOU", erro.getCause().getSuppressed()[0].getMessage());
            assertEquals(0, banco.resetsAutoCommit);
            assertEquals(1, banco.aborts);
            assertEquals(1, banco.closes);
            assertTrue(banco.pendentes.isEmpty());
            assertTrue(banco.confirmados.isEmpty());
            assertEquals(0, banco.commitsImplicitos);
        }));
    }

    @TestFactory
    Stream<DynamicTest> falhaDeFechamentoDeStatementAindaEhAnteriorAoCommit() {
        return Stream.of(Fluxo.values()).map(fluxo -> DynamicTest.dynamicTest(fluxo + " close do statement falho", () -> {
            Banco banco = new Banco(); banco.falhaCloseStatement = true;
            DbException erro = assertThrows(DbException.class, () -> fluxo.executar(banco));
            assertFalse(erro instanceof ResultadoTransacaoIncertoException);
            assertEquals("STATEMENT FALHOU AO FECHAR", erro.getMessage());
            assertTrue(banco.escritas > 0);
            assertEquals(0, banco.commits);
            assertEquals(1, banco.rollbacks);
            assertTrue(banco.confirmados.isEmpty());
        }));
    }

    @TestFactory
    Stream<DynamicTest> erroDeLimpezaAposRollbackNaoOcultaFalhaOriginal() {
        return Stream.of(Fluxo.values()).map(fluxo -> DynamicTest.dynamicTest(fluxo + " preserva erro de gravação", () -> {
            Banco banco = new Banco(); banco.falhaNaEscrita = 2; banco.falhaReset = true; banco.falhaClose = true;
            DbException erro = assertThrows(DbException.class, () -> fluxo.executar(banco));
            assertSame(banco.erroEscrita, erro.getCause());
            assertEquals(List.of("RESET FALHOU", "CLOSE FALHOU"),
                    Stream.of(erro.getCause().getSuppressed()).map(Throwable::getMessage).collect(java.util.stream.Collectors.toList()));
            assertEquals(0, banco.commits);
            assertTrue(banco.confirmados.isEmpty());
        }));
    }

    @TestFactory
    Stream<DynamicTest> erroDeCommitTemResultadoIncertoMesmoComRollbackPosterior() {
        return Stream.of(Fluxo.values()).flatMap(fluxo -> Stream.of(false, true).map(confirmouAntesDeFalhar ->
                DynamicTest.dynamicTest(fluxo + " commit incerto, servidor confirmou=" + confirmouAntesDeFalhar, () -> {
                    Banco banco = new Banco(); banco.falhaCommit = true; banco.commitAntesDeFalhar = confirmouAntesDeFalhar;
                    ResultadoTransacaoIncertoException erro = assertThrows(ResultadoTransacaoIncertoException.class,
                            () -> fluxo.executar(banco));
                    assertEquals("COMMIT SEM RESPOSTA", erro.getCause().getMessage());
                    assertTrue(erro.getMessage().contains("Não repita"));
                    assertTrue(erro.getMessage().contains("conferir o histórico"));
                    assertEquals(1, banco.commits);
                    assertEquals(1, banco.rollbacks);
                    assertEquals(confirmouAntesDeFalhar, !banco.confirmados.isEmpty());
                    assertEquals(0, banco.commitsImplicitos);
                })));
    }

    @TestFactory
    Stream<DynamicTest> falhasDeDescarteContinuamSuprimidasSemReligarAutoCommit() {
        return Stream.of(Fluxo.values()).map(fluxo -> DynamicTest.dynamicTest(fluxo + " descarte também falhou", () -> {
            Banco banco = new Banco(); banco.falhaNaEscrita = 2; banco.falhaRollback = true;
            banco.falhaAbort = true; banco.falhaClose = true;
            ResultadoTransacaoIncertoException erro = assertThrows(ResultadoTransacaoIncertoException.class,
                    () -> fluxo.executar(banco));
            assertSame(banco.erroEscrita, erro.getCause());
            assertEquals(3, erro.getCause().getSuppressed().length);
            assertEquals(0, banco.resetsAutoCommit);
            assertEquals(0, banco.commitsImplicitos);
        }));
    }

    private static <T> T proxy(Class<T> tipo, InvocationHandler handler) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler));
    }

    private static final class Banco {
        final List<String> pendentes = new ArrayList<>();
        final List<String> confirmados = new ArrayList<>();
        final SQLException erroEscrita = new SQLException("ESCRITA FALHOU");
        boolean falhaConsulta, falhaReset, falhaClose, falhaRollback, falhaAbort, falhaCommit, commitAntesDeFalhar;
        boolean falhaCloseStatement;
        int falhaNaEscrita, escritas, commits, rollbacks, resetsAutoCommit, commitsImplicitos, closes, aborts;

        Connection conexao() {
            return proxy(Connection.class, (p, metodo, args) -> {
                switch (metodo.getName()) {
                    case "prepareStatement": return statement((String) args[0]);
                    case "setAutoCommit":
                        if (Boolean.TRUE.equals(args[0])) {
                            resetsAutoCommit++;
                            if (falhaReset) throw new SQLException("RESET FALHOU");
                            if (!pendentes.isEmpty()) { commitsImplicitos++; confirmar(); }
                        }
                        return null;
                    case "commit":
                        commits++;
                        if (!falhaCommit || commitAntesDeFalhar) confirmar();
                        if (falhaCommit) throw new SQLException("COMMIT SEM RESPOSTA");
                        return null;
                    case "rollback":
                        rollbacks++;
                        if (falhaRollback) throw new SQLException("ROLLBACK FALHOU");
                        pendentes.clear(); return null;
                    case "abort":
                        aborts++;
                        if (falhaAbort) throw new SQLException("ABORT FALHOU");
                        pendentes.clear(); return null;
                    case "close":
                        closes++;
                        if (falhaClose) throw new SQLException("CLOSE FALHOU");
                        return null;
                    default: throw new AssertionError("Connection não esperado: " + metodo.getName());
                }
            });
        }

        void confirmar() { confirmados.addAll(pendentes); pendentes.clear(); }

        void escrever(String sql) throws SQLException {
            escritas++;
            if (escritas == falhaNaEscrita) throw erroEscrita;
            pendentes.add(sql);
        }

        PreparedStatement statement(String sql) {
            return proxy(PreparedStatement.class, (p, metodo, args) -> {
                String nome = metodo.getName();
                if (nome.startsWith("set") || nome.equals("addBatch")) return null;
                if (nome.equals("close")) {
                    if (falhaCloseStatement && !pendentes.isEmpty()) {
                        falhaCloseStatement = false;
                        throw new SQLException("STATEMENT FALHOU AO FECHAR");
                    }
                    return null;
                }
                if (nome.equals("getGeneratedKeys")) return linhas(List.of(Map.of("1", 401)));
                if (nome.equals("executeUpdate")) { escrever(sql); return 1; }
                if (nome.equals("executeBatch")) { escrever(sql); return new int[]{1}; }
                if (nome.equals("executeQuery")) {
                    if (falhaConsulta) throw new SQLException("CONSULTA FALHOU");
                    if (sql.startsWith("SELECT id_caixa")) return linhas(List.of(Map.of("1", 10)));
                    if (sql.startsWith("SELECT aberto_em")) return linhas(List.of(Map.of("1", Timestamp.valueOf(DATA.minusHours(1)))));
                    if (sql.startsWith("SELECT status")) return linhas(List.of(Map.of(
                            "status", "PAGO", "data_pedido", Timestamp.valueOf(DATA), "valor_total", DEZ)));
                    if (sql.startsWith("SELECT id_produto, COALESCE(SUM(estoque_baixado)")) return linhas(List.of(Map.of("id_produto", 7, "repor", BigDecimal.ONE, "legados", 0)));
                    if (sql.startsWith("SELECT metodo_pagamento")) return linhas(List.of(Map.of("1", "DINHEIRO", "2", DEZ)));
                    if (sql.startsWith("SELECT pg.id_pagamento")) return linhas(List.of(Map.of(
                            "id_pagamento", 11, "id_pedido", 401, "id_caixa", 10, "valor_pago", DEZ)));
                }
                throw new AssertionError("Statement não esperado: " + nome + ": " + sql);
            });
        }

        ResultSet linhas(List<Map<String, Object>> dados) {
            int[] indice = {-1};
            return proxy(ResultSet.class, (p, metodo, args) -> {
                String nome = metodo.getName();
                if (nome.equals("next")) return ++indice[0] < dados.size();
                if (nome.equals("close")) return null;
                Object valor = dados.get(indice[0]).get(String.valueOf(args[0]));
                switch (nome) {
                    case "getInt": return ((Number) valor).intValue();
                    case "getBigDecimal": return valor;
                    case "getTimestamp": return valor;
                    case "getString": return String.valueOf(valor);
                    default: throw new AssertionError("ResultSet não esperado: " + nome);
                }
            });
        }
    }
}
