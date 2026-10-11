package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regras de produtos que não podem ser vendidos no fiado. */
class OrderFiadoTest {

    private static BigDecimal r(String v) { return Money.parse(v); }

    private static Product produto(String nome, String preco, boolean permiteFiado) {
        Product p = new Product("cod-" + nome, nome, r(preco), 100);
        p.setPermiteFiado(permiteFiado);
        return p;
    }

    /** Venda: 1 cigarro (R$ 15, sem fiado) + 2 pães (R$ 5 cada) = R$ 25. */
    private static Order vendaCigarroEPao() {
        Order o = new Order();
        List<OrderItem> itens = new ArrayList<>();
        itens.add(new OrderItem(produto("cigarro", "15", false), 1, r("15")));
        itens.add(new OrderItem(produto("pao", "5", true), 2, r("5")));
        o.setItemPedido(itens);
        o.setValorTotal(r("25"));
        return o;
    }

    @Test
    void fiadoSoCobreOsProdutosPermitidos() {
        Order o = vendaCigarroEPao();
        assertEquals(r("10"), o.getTotalPermitidoNoFiado());
        assertEquals(r("10"), o.getFiadoAindaPermitido());
        assertEquals(1, o.getItensSemFiado().size());
        assertEquals("cigarro", o.getItensSemFiado().get(0).getNomeProduto());
    }

    @Test
    void cigarroPagoEmDinheiroLiberaORestoNoFiado() {
        Order o = vendaCigarroEPao();
        assertEquals(r("15"), o.getFaltaPagarNaHoraParaItensSemFiado());
        o.getPagamentos().add(new Order.PaymentSlice(r("15"), PaymentMethod.DINHEIRO));
        assertEquals(Money.ZERO, o.getFaltaPagarNaHoraParaItensSemFiado());
        assertEquals(r("10"), o.getSaldo());
        assertEquals(r("10"), o.getFiadoAindaPermitido());   // os 10 que faltam podem ir para o fiado
    }

    @Test
    void fiadoJaLancadoReduzOQueAindaCabe() {
        Order o = vendaCigarroEPao();
        o.getPagamentos().add(new Order.PaymentSlice(r("4"), PaymentMethod.FIADO));
        assertEquals(r("6"), o.getFiadoAindaPermitido());
    }

    @Test
    void tirarOCigarroDaVendaRecalculaTudo() {
        Order o = vendaCigarroEPao();
        o.removerItens(o.getItensSemFiado());
        assertEquals(r("10"), o.getValorTotal());
        assertEquals(1, o.getItemPedido().size());
        assertTrue(o.getItensSemFiado().isEmpty());
        assertEquals(r("10"), o.getFiadoAindaPermitido());
        assertEquals(r("10"), o.getSaldo());
    }

    @Test
    void vendaSemProdutosBloqueadosPodeIrTodaParaOFiado() {
        Order o = new Order();
        List<OrderItem> itens = new ArrayList<>();
        itens.add(new OrderItem(produto("arroz", "30", true), 1, r("30")));
        o.setItemPedido(itens);
        o.setValorTotal(r("30"));
        assertEquals(r("30"), o.getFiadoAindaPermitido());
        assertEquals(Money.ZERO, o.getFaltaPagarNaHoraParaItensSemFiado());
    }

    @Test
    void vendaSoDeCigarroNaoTemNadaParaOFiado() {
        Order o = new Order();
        List<OrderItem> itens = new ArrayList<>();
        itens.add(new OrderItem(produto("cigarro", "15", false), 2, r("15")));
        o.setItemPedido(itens);
        o.setValorTotal(r("30"));
        assertEquals(Money.ZERO, o.getFiadoAindaPermitido());
    }
}
