package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A tela conserva objetos antigos; o JDBC de teste entrega as regras atuais. */
class OrderDaoJdbcFiadoTest {
    static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 1, 12, 0);
    static final Clock RELOGIO = Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    static BigDecimal r(String v) { return new BigDecimal(v).setScale(2); }
    static <T> T proxy(Class<T> tipo, InvocationHandler h) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, h));
    }
    static class Compra {
        final int id; final BigDecimal total, pago; final LocalDateTime data;
        Compra(int id, String total, String pago, int dias) {
            this.id = id; this.total = r(total); this.pago = r(pago); data = AGORA.minusDays(dias);
        }
    }
    static class Banco {
        BigDecimal limite = r("100"); String status = "DISPONIVEL"; boolean existe = true;
        final List<Compra> compras = new ArrayList<>();
        final Map<Integer, Boolean> permissoes = new TreeMap<>(Map.of(1, true, 2, false));
        final List<String> consultas = new ArrayList<>(), escritas = new ArrayList<>();
        int commits, rollbacks;
        OrderDaoJdbc dao() { return new OrderDaoJdbc(this::conectar, RELOGIO); }
        Connection conectar() {
            return proxy(Connection.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "prepareStatement": return statement((String) a[0]);
                    case "commit": commits++; return null;
                    case "rollback": rollbacks++; return null;
                    case "setAutoCommit": case "close": return null;
                    default: throw new AssertionError(m.getName());
                }
            });
        }
        PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new HashMap<>();
            return proxy(PreparedStatement.class, (p, m, a) -> {
                String n = m.getName();
                if (n.startsWith("set")) { params.put((Integer) a[0], a[1]); return null; }
                if (n.equals("close") || n.equals("addBatch")) return null;
                if (n.equals("executeUpdate")) { escritas.add(sql); return 1; }
                if (n.equals("executeBatch")) { escritas.add(sql); return new int[]{1}; }
                if (n.equals("getGeneratedKeys")) return rows(List.of(Map.of("1", 500)));
                if (!n.equals("executeQuery")) throw new AssertionError(n);
                consultas.add(sql);
                if (sql.startsWith("SELECT id_caixa")) return rows(List.of(Map.of("1", 1, "aberto_em", Timestamp.valueOf(AGORA.minusHours(4)))));
                if (sql.startsWith("SELECT id_cliente, limite")) return rows(existe ? List.of(Map.of("limite_credito", limite, "situacao_fiado", status)) : List.of());
                if (sql.startsWith("SELECT id_pedido, data_pedido")) {
                    List<Map<String, Object>> resultado = new ArrayList<>();
                    for (Compra c : compras) resultado.add(Map.of("id_pedido", c.id, "valor_total", c.total,
                            "data_pedido", Timestamp.valueOf(c.data), "desconto", r("0")));
                    return rows(resultado);
                }
                if (sql.startsWith("SELECT valor_pago")) return rows(compras.stream()
                        .filter(c -> c.id == (Integer) params.get(1)).map(c -> Map.<String, Object>of("valor_pago", c.pago))
                        .collect(java.util.stream.Collectors.toList()));
                if (sql.startsWith("SELECT nome_produto, permite_fiado")) {
                    Integer id = (Integer) params.get(1);
                    return rows(permissoes.containsKey(id) ? List.of(Map.of("nome_produto", "Produto " + id, "permite_fiado", permissoes.get(id))) : List.of());
                }
                if (sql.startsWith("SELECT aberto_em")) return rows(List.of(Map.of("1", Timestamp.valueOf(AGORA.minusHours(4)))));
                if (sql.startsWith("SELECT status")) return rows(List.of(Map.of("status", "FIADO", "data_pedido", Timestamp.valueOf(AGORA.minusHours(1)), "valor_total", r("80"))));
                if (sql.startsWith("SELECT metodo_pagamento") || sql.startsWith("SELECT id_produto, COALESCE")) return rows(List.of());
                throw new AssertionError(sql);
            });
        }
        ResultSet rows(List<Map<String, Object>> dados) {
            int[] i = {-1};
            return proxy(ResultSet.class, (p, m, a) -> {
                if (m.getName().equals("next")) return ++i[0] < dados.size();
                if (m.getName().equals("close")) return null;
                Object valor = dados.get(i[0]).get(String.valueOf(a[0]));
                if (m.getName().equals("getInt")) return ((Number) valor).intValue();
                return valor;
            });
        }
        void recusa(Order venda, String trecho) {
            DbException erro = assertThrows(DbException.class, () -> dao().insert(venda));
            assertTrue(erro.getMessage().contains(trecho), erro.getMessage());
            assertEquals(0, commits); assertEquals(1, rollbacks);
            assertTrue(escritas.isEmpty(), "A recusa deve ocorrer antes de inserir ou cancelar a venda");
        }
    }
    static Order venda(String valor) {
        Customer antigo = new Customer("Cliente", r("100"), CustomerStatus.DISPONIVEL); antigo.setIdCliente(9);
        Product antigoProduto = new Product("1", "Produto antigo", r(valor), 100); antigoProduto.setIdProduto(1);
        Order o = new Order(); o.setCostumer(antigo); o.setStatus(OrderStatus.FIADO);
        o.setItemPedido(new ArrayList<>(List.of(new OrderItem(antigoProduto, 1, r(valor)))));
        o.aplicarDesconto(r("0")); o.getPagamentos().add(new Order.PaymentSlice(r(valor), PaymentMethod.FIADO));
        return o;
    }
    @Test void bloqueioAtualRecusaClienteQueEstavaDisponivelNaTela() {
        Banco b = new Banco(); b.status = "BLOQUEADO"; b.recusa(venda("80"), "BLOQUEADO");
    }
    @Test void limiteReduzidoOuDividaNovaRecusamSaldoAntigoDaTela() {
        Banco menor = new Banco(); menor.limite = r("70"); menor.recusa(venda("80"), "Limite de crédito");
        Banco ocupado = new Banco(); ocupado.compras.add(new Compra(10, "30", "0", 1)); ocupado.recusa(venda("80"), "Limite de crédito");
    }
    @Test void atrasoAtualMesmoComPagamentoParcialBloqueia() {
        Banco b = new Banco(); b.compras.add(new Compra(10, "100", "90", 31)); b.recusa(venda("1"), "bloqueado por atraso");
    }
    @Test void saldoECalculadoComPagamentosAtuaisEAceitaLimiteExato() {
        Banco b = new Banco(); b.compras.add(new Compra(10, "100", "80", 2)); b.dao().insert(venda("80"));
        assertEquals(1, b.commits); assertEquals(0, b.rollbacks);
        assertTrue(b.consultas.get(0).contains("FROM caixa")); assertTrue(b.consultas.get(1).contains("FROM cliente"));
        assertTrue(b.consultas.stream().allMatch(s -> s.endsWith("FOR UPDATE")));
    }
    @Test void permissaoAtualDoProdutoRecusaObjetoAntigoPermitido() {
        Banco b = new Banco(); b.permissoes.put(1, false); b.recusa(venda("80"), "não podem ser vendidos no fiado: Produto 1");
    }
    @Test void produtoProibidoPodeSerPagoAVistaComRestanteNoFiado() {
        Banco b = new Banco(); Order o = venda("30");
        Product proibido = new Product("2", "Proibido", r("20"), 10); proibido.setIdProduto(2);
        o.getItemPedido().add(new OrderItem(proibido, 1, r("20"))); o.aplicarDesconto(r("0"));
        o.getPagamentos().add(new Order.PaymentSlice(r("20"), PaymentMethod.PIX));
        b.dao().insert(o); assertEquals(1, b.commits);
    }
    @Test void trocaSubstituiDividaOriginalSemContarDuasVezes() {
        Banco b = new Banco(); b.compras.add(new Compra(10, "80", "0", 0)); b.compras.add(new Compra(11, "20", "0", 1));
        Order o = venda("80"); o.setIdPedidoTrocado(10); b.dao().insert(o); assertEquals(1, b.commits);
    }
    @Test void trocaRecusadaNaoCancelaOriginal() {
        Banco b = new Banco(); b.compras.add(new Compra(10, "80", "0", 0)); b.compras.add(new Compra(11, "20", "0", 1));
        Order o = venda("90"); o.setIdPedidoTrocado(10); b.recusa(o, "Limite de crédito");
    }
    @Test void clienteAusenteOuFiadoSemClienteNaoGravam() {
        Banco b = new Banco(); b.existe = false; b.recusa(venda("1"), "cliente não foi encontrado");
        Banco sem = new Banco(); Order o = venda("1"); o.setCostumer(null); sem.recusa(o, "cliente válido");
    }
    @Test void vendaAVistaContinuaPermitidaParaClienteBloqueado() {
        Banco b = new Banco(); b.status = "BLOQUEADO"; Order o = venda("80");
        o.setStatus(OrderStatus.PAGO); o.getPagamentos().clear(); o.getPagamentos().add(new Order.PaymentSlice(r("80"), PaymentMethod.PIX));
        b.dao().insert(o); assertEquals(1, b.commits); assertEquals(1, b.consultas.size());
    }
}
