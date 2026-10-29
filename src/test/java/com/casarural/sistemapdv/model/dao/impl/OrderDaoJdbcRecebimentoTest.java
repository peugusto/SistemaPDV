package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** JDBC em memória: verifica a transação sem conectar ao banco da loja. */
class OrderDaoJdbcRecebimentoTest {
    private static BigDecimal r(String v) { return Money.parse(v); }
    private static Map<String,Object> row(Object... pares) {
        Map<String,Object> linha = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) linha.put(pares[i].toString(), pares[i + 1]);
        return linha;
    }
    private static <T> T proxy(Class<T> tipo, InvocationHandler handler) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler));
    }
    private static ResultSet result(List<Map<String,Object>> linhas) {
        int[] posicao = {-1};
        return proxy(ResultSet.class, (p, m, a) -> {
            if (m.getName().equals("next")) return ++posicao[0] < linhas.size();
            if (m.getName().equals("close")) return null;
            Object valor = linhas.get(posicao[0]).get(a[0].toString());
            switch (m.getName()) {
                case "getInt": return valor == null ? 0 : ((Number) valor).intValue();
                case "getString": return valor == null ? null : valor.toString();
                case "getBigDecimal": return valor;
                case "getTimestamp": return valor;
                default: throw new AssertionError("ResultSet inesperado: " + m.getName());
            }
        });
    }

    private static class Banco {
        java.time.Clock relogio = java.time.Clock.fixed(java.time.LocalDateTime.of(2026,10,29,12,0).atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault());
        LocalDateTime abertura = LocalDateTime.of(2026,10,29,8,0);
        boolean confirmou, caixaAberto = true, falhaLimpeza, semChave, falhaRollback;
        int conexoes, insercoes, rollbacks, falharNaInsercao, resetsAutoCommit;
        final List<Map<String,Object>> pendentes = new ArrayList<>(), gravados = new ArrayList<>();
        final List<Integer> quitados = new ArrayList<>(), produtosConsultados = new ArrayList<>();
        final List<String> consultas = new ArrayList<>();
        final List<Map<String,Object>> compras = List.of(compra(101, "50", "5"), compra(102, "40", "0"), compra(103, "10", "0"));

        static Map<String,Object> compra(int id, String total, String desconto) {
            return row("id_pedido", id, "valor_total", r(total), "desconto", r(desconto),
                    "data_pedido", Timestamp.valueOf(LocalDateTime.of(2026, 10, id - 100, 10, 0)));
        }
        OrderDaoJdbc dao() {
            return new OrderDaoJdbc(() -> {
                conexoes++;
                return proxy(Connection.class, (p, m, a) -> {
                    switch (m.getName()) {
                        case "prepareStatement": return statement((String) a[0]);
                        case "setAutoCommit":
                            if (Boolean.TRUE.equals(a[0])) resetsAutoCommit++;
                            if (Boolean.TRUE.equals(a[0]) && falhaLimpeza) throw new SQLException("Limpeza simulada");
                            return null;
                        case "commit": confirmou = true; gravados.addAll(pendentes); pendentes.clear(); return null;
                        case "rollback":
                            rollbacks++;
                            if (falhaRollback) throw new SQLException("Rollback simulado falhou");
                            pendentes.clear(); quitados.clear(); return null;
                        case "close": return null;
                        default: throw new AssertionError("Connection inesperada: " + m.getName());
                    }
                });
            }, relogio);
        }
        PreparedStatement statement(String sql) {
            Map<Integer,Object> parametros = new HashMap<>();
            int[] chave = {0};
            return proxy(PreparedStatement.class, (p, m, a) -> {
                String acao = m.getName();
                if (acao.startsWith("set")) { parametros.put((Integer) a[0], a[1]); return null; }
                if (acao.equals("close")) return null;
                if (acao.equals("getGeneratedKeys")) return result(semChave ? List.of() : List.of(row("1", chave[0])));
                if (acao.equals("executeQuery")) {
                    consultas.add(sql);
                    if (sql.contains("FROM cliente")) {
                        assertTrue(sql.endsWith("FOR UPDATE"));
                        return result(List.of(row("id_cliente", 9, "nome_cliente", "Maria da Silva", "telefone", "11987654321")));
                    }
                    if (sql.contains("FROM caixa")) return result(caixaAberto ? List.of(row("1", 22, "aberto_em", Timestamp.valueOf(abertura))) : List.of());
                    if (sql.contains("FROM pedido WHERE")) {
                        assertTrue(sql.contains("ORDER BY data_pedido, id_pedido FOR UPDATE"));
                        return result(compras);
                    }
                    int id = (Integer) parametros.get(1);
                    if (sql.startsWith("SELECT valor_pago")) {
                        assertTrue(sql.endsWith("FOR UPDATE"));
                        return result(id == 101 ? List.of(row("valor_pago", r("20"))) : List.of());
                    }
                    if (sql.contains("FROM itempedido")) {
                        produtosConsultados.add(id);
                        return result(List.of(row("nome_produto", id == 101 ? "Arroz" : id == 102 ? "Feijão" : "Café",
                                "quantidade", 2, "preco_unitario", r(id == 101 ? "27,50" : id == 102 ? "20" : "5"))));
                    }
                }
                if (acao.equals("executeUpdate")) {
                    if (sql.startsWith("INSERT INTO pagamento")) {
                        insercoes++;
                        if (insercoes == falharNaInsercao) throw new SQLException("Inserção simulada falhou");
                        chave[0] = 899 + insercoes;
                        pendentes.add(row("pedido", parametros.get(1), "valor", parametros.get(2),
                                "forma", parametros.get(3), "data", parametros.get(4), "caixa", parametros.get(5)));
                    } else if (sql.startsWith("UPDATE pedido")) quitados.add((Integer) parametros.get(1));
                    else throw new AssertionError("Update inesperado: " + sql);
                    return 1;
                }
                throw new AssertionError("PreparedStatement inesperado: " + acao + " / " + sql);
            });
        }
    }

    @Test
    void retornaSomenteComprasAbatidasEProdutosDaMesmaTransacao() {
        Banco banco = new Banco();
        RecebimentoFiado rec = banco.dao().registerPartialPayment(9, r("35"), PaymentMethod.PIX);
        assertTrue(banco.confirmou);
        assertEquals(1, banco.conexoes);
        assertTrue(banco.consultas.get(0).contains("FROM cliente"));
        assertEquals(900, rec.getIdRecebimento());
        assertEquals("Maria da Silva", rec.getNomeCliente());
        assertEquals("11987654321", rec.getTelefone());
        assertEquals(r("80"), rec.getDividaAntes());
        assertEquals(r("45"), rec.getDividaDepois());
        assertEquals(List.of(101, 102), banco.produtosConsultados);
        assertEquals(2, rec.getCompras().size());
        assertEquals(r("30"), rec.getCompras().get(0).getValorPago());
        assertTrue(rec.getCompras().get(0).isQuitada());
        assertEquals(r("5"), rec.getCompras().get(1).getValorPago());
        assertFalse(rec.getCompras().get(1).isQuitada());
        assertEquals(List.of(101), banco.quitados);
        assertEquals(r("20"), rec.getCompras().get(0).getPagoAntes());
        assertEquals(r("27,50"), rec.getCompras().get(0).getProdutos().get(0).getPrecoUnitario());
        assertEquals(banco.gravados.get(0).get("data"), banco.gravados.get(1).get("data"));
    }

    @Test
    void quitarTudoRetornaTodosOsProdutosESaldoZero() {
        Banco banco = new Banco();
        RecebimentoFiado rec = banco.dao().registerPartialPayment(9, r("80"), PaymentMethod.DINHEIRO);
        assertEquals(Money.ZERO, rec.getDividaDepois());
        assertEquals(3, rec.getCompras().size());
        assertTrue(rec.getCompras().stream().allMatch(RecebimentoFiado.Compra::isQuitada));
        assertEquals(List.of(101, 102, 103), banco.quitados);
    }

    @Test
    void valorMenorQuePrimeiraCompraNaoIncluiProdutosDasDemais() {
        Banco banco = new Banco();
        RecebimentoFiado rec = banco.dao().registerPartialPayment(9, r("20"), PaymentMethod.CARTAO);
        assertEquals(List.of(101), banco.produtosConsultados);
        assertEquals(r("10"), rec.getCompras().get(0).getSaldoDepois());
        assertTrue(banco.quitados.isEmpty());
    }

    @Test
    void recusaValorAcimaDoSaldoAtualSemGravarRecebimento() {
        Banco banco = new Banco();
        assertThrows(IllegalArgumentException.class, () -> banco.dao().registerPartialPayment(9, r("81"), PaymentMethod.PIX));
        assertFalse(banco.confirmou);
        assertEquals(0, banco.insercoes);
        assertEquals(1, banco.rollbacks);
    }

    @Test
    void falhaNaSegundaCompraDesfazTodoORecebimento() {
        Banco banco = new Banco(); banco.falharNaInsercao = 2;
        assertThrows(DbException.class, () -> banco.dao().registerPartialPayment(9, r("35"), PaymentMethod.PIX));
        assertFalse(banco.confirmou);
        assertTrue(banco.gravados.isEmpty());
        assertTrue(banco.pendentes.isEmpty());
        assertEquals(1, banco.rollbacks);
    }

    @Test
    void caixaFechadoOuRecebimentoSemIdentificadorNaoRetornaComprovante() {
        Banco fechado = new Banco(); fechado.caixaAberto = false;
        assertThrows(DbException.class, () -> fechado.dao().registerPartialPayment(9, r("10"), PaymentMethod.PIX));
        assertFalse(fechado.confirmou);
        Banco semId = new Banco(); semId.semChave = true;
        assertThrows(DbException.class, () -> semId.dao().registerPartialPayment(9, r("10"), PaymentMethod.PIX));
        assertFalse(semId.confirmou);
        assertTrue(semId.gravados.isEmpty());
    }

    @Test
    void erroDeLimpezaDepoisDoCommitNaoAnunciaPagamentoNaoSalvo() {
        Banco banco = new Banco(); banco.falhaLimpeza = true;
        RecebimentoFiado rec = banco.dao().registerPartialPayment(9, r("10"), PaymentMethod.PIX);
        assertTrue(banco.confirmou);
        assertEquals(900, rec.getIdRecebimento());
        assertEquals(1, banco.gravados.size());
    }

    @Test
    void rollbackQueFalhouNaoPodeConfirmarPendenciasAoReligarAutoCommit() {
        Banco banco = new Banco(); banco.falharNaInsercao = 2; banco.falhaRollback = true;
        DbException erro = assertThrows(DbException.class,
                () -> banco.dao().registerPartialPayment(9, r("35"), PaymentMethod.PIX));
        assertFalse(banco.confirmou);
        assertEquals(0, banco.resetsAutoCommit);
        assertEquals(1, erro.getCause().getSuppressed().length);
    }

    @Test
    void entradasInvalidasNaoAbremConexao() {
        Banco banco = new Banco(); OrderDaoJdbc dao = banco.dao();
        assertThrows(IllegalArgumentException.class, () -> dao.registerPartialPayment(9, Money.ZERO, PaymentMethod.PIX));
        assertThrows(IllegalArgumentException.class, () -> dao.registerPartialPayment(9, r("10"), PaymentMethod.FIADO));
        assertThrows(IllegalArgumentException.class, () -> dao.registerPartialPayment(null, r("10"), PaymentMethod.PIX));
        assertEquals(0, banco.conexoes);
    }
    @Test
    void caixaDeOutroDiaRecusaRecebimentoAntesDeQualquerEscrita() {
        Banco banco = new Banco(); banco.abertura = banco.abertura.minusDays(1);
        DbException erro = assertThrows(DbException.class,
                () -> banco.dao().registerPartialPayment(9, r("10"), PaymentMethod.PIX));
        assertTrue(erro.getMessage().contains("outro dia"));
        assertEquals(0, banco.insercoes); assertFalse(banco.confirmou);
        assertEquals(1, banco.rollbacks); assertTrue(banco.gravados.isEmpty());
        assertTrue(banco.consultas.stream().filter(q -> q.contains("FROM caixa")).allMatch(q -> q.endsWith("FOR UPDATE")));
    }

    @Test
    void mesmaInstanciaRevalidaDiaDepoisDaMeiaNoite() {
        Banco banco = new Banco();
        java.time.ZoneId zona = java.time.ZoneId.of("America/Sao_Paulo");
        java.util.concurrent.atomic.AtomicReference<java.time.Instant> agora = new java.util.concurrent.atomic.AtomicReference<>(
                LocalDateTime.of(2026,10,29,23,59).atZone(zona).toInstant());
        banco.relogio = new java.time.Clock() {
            public java.time.ZoneId getZone() { return zona; }
            public java.time.Clock withZone(java.time.ZoneId outra) { return java.time.Clock.fixed(instant(), outra); }
            public java.time.Instant instant() { return agora.get(); }
        };
        OrderDaoJdbc dao = banco.dao();
        dao.registerPartialPayment(9, r("10"), PaymentMethod.PIX);
        int insercoes = banco.insercoes;
        agora.set(agora.get().plusSeconds(120));
        DbException erro = assertThrows(DbException.class, () -> dao.registerPartialPayment(9, r("10"), PaymentMethod.PIX));
        assertTrue(erro.getMessage().contains("outro dia")); assertEquals(insercoes, banco.insercoes);
        assertEquals(1, banco.rollbacks);
    }

}
