package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.casarural.sistemapdv.model.entities.enums.PaymentMethod.*;
import static org.junit.jupiter.api.Assertions.*;

/** Desconto e crédito da troca. */
class TrocaDescontoTest {

    private static BigDecimal r(String v) { return Money.parse(v); }

    private static Map<PaymentMethod, BigDecimal> pago(Object... formaValor) {
        Map<PaymentMethod, BigDecimal> m = new EnumMap<>(PaymentMethod.class);
        for (int i = 0; i < formaValor.length; i += 2) m.put((PaymentMethod) formaValor[i], r((String) formaValor[i + 1]));
        return m;
    }

    // ---------------- Desconto ----------------

    @Test
    void descontoEmReais() {
        assertEquals(r("2,50"), RegraDesconto.calcular(r("20"), "2,50", false));
    }

    @Test
    void descontoEmPorcentagemArredondaNoCentavo() {
        assertEquals(r("2"), RegraDesconto.calcular(r("20"), "10", true));
        assertEquals(r("0,33"), RegraDesconto.calcular(r("3,33"), "10", true));
    }

    @Test
    void descontoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> RegraDesconto.calcular(r("20"), "20", false));  // = compra
        assertThrows(IllegalArgumentException.class, () -> RegraDesconto.calcular(r("20"), "25", false));  // > compra
        assertThrows(IllegalArgumentException.class, () -> RegraDesconto.calcular(r("20"), "100", true));
        assertThrows(IllegalArgumentException.class, () -> RegraDesconto.calcular(r("20"), "abc", false));
        assertThrows(IllegalArgumentException.class, () -> RegraDesconto.calcular(r("20"), "", false));
    }

    @Test
    void percentualParaMostrar() {
        assertEquals("10%", RegraDesconto.percentual(r("2"), r("20")));
        assertEquals("12,5%", RegraDesconto.percentual(r("2,50"), r("20")));
    }

    @Test
    void vendaComDescontoRecalculaOTotal() {
        Order o = new Order();
        List<OrderItem> itens = new ArrayList<>();
        Product p = new Product("1", "x", r("10"), 10);
        itens.add(new OrderItem(p, 2, r("10")));
        o.setItemPedido(itens);
        o.aplicarDesconto(r("3"));
        assertEquals(r("20"), o.getSubtotalItens());
        assertEquals(r("17"), o.getValorTotal());
        // Alterar os itens remove o desconto da composição anterior.
        o.removerItens(new ArrayList<>(itens));
        assertEquals(r("0"), o.getValorTotal());
        assertEquals(r("0"), o.getDesconto());
    }

    // ---------------- Troca ----------------

    @Test
    void trocaPorProdutoMaisCaroPagaSoADiferenca() {
        RegraTroca.Resultado t = RegraTroca.calcular(pago(DINHEIRO, "10"), r("12"));
        assertEquals(r("10"), t.getTotalCredito());
        assertTrue(t.getDevolver().isEmpty());
        assertEquals(DINHEIRO, t.getCreditos().get(0).getMetodo());
        assertTrue(t.getCreditos().get(0).isCredito());
    }

    @Test
    void trocaPorProdutoMaisBaratoDevolveEmDinheiro() {
        RegraTroca.Resultado t = RegraTroca.calcular(pago(DINHEIRO, "10"), r("7"));
        assertEquals(r("7"), t.getTotalCredito());
        assertEquals(r("3"), t.getDevolver().get(DINHEIRO));
    }

    @Test
    void sobraSaiPrimeiroDoFiado() {
        // pagou 5 no PIX e deixou 10 no fiado; a nova custa 12 → o fiado cai para 7, nada a devolver
        RegraTroca.Resultado t = RegraTroca.calcular(pago(PIX, "5", FIADO, "10"), r("12"));
        assertEquals(r("12"), t.getTotalCredito());
        assertTrue(t.getDevolver().isEmpty());
        BigDecimal fiado = t.getCreditos().stream().filter(c -> c.getMetodo() == FIADO).findFirst().orElseThrow().getValor();
        assertEquals(r("7"), fiado);
    }

    @Test
    void depoisDoFiadoDevolveDinheiroAntesDoPix() {
        RegraTroca.Resultado t = RegraTroca.calcular(pago(PIX, "5", DINHEIRO, "5", FIADO, "2"), r("4"));
        // sobra 8: 2 do fiado, 5 do dinheiro, 1 do PIX
        assertEquals(r("5"), t.getDevolver().get(DINHEIRO));
        assertEquals(r("1"), t.getDevolver().get(PIX));
        assertEquals(r("6"), t.getTotalDevolver());
        assertEquals(r("4"), t.getTotalCredito());
    }

    @Test
    void trocaSemDiferenca() {
        RegraTroca.Resultado t = RegraTroca.calcular(pago(CARTAO, "9,90"), r("9,90"));
        assertEquals(r("9,90"), t.getTotalCredito());
        assertTrue(t.getDevolver().isEmpty());
    }

    @Test
    void textoDaDevolucao() {
        Map<PaymentMethod, BigDecimal> d = pago(DINHEIRO, "2", PIX, "1");
        assertEquals("R$ 2,00 em dinheiro e R$ 1,00 por PIX", RegraTroca.descreverDevolucao(d).replace(' ', ' '));
    }
}
