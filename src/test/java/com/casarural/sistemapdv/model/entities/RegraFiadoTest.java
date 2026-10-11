package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegraFiadoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 11, 10, 0);

    private static BigDecimal r(String v) { return Money.parse(v); }

    /** Compra fiada feita "dias" atrás, de "valor", com "pagoDepois" já pago. */
    private static Order compra(int dias, String valor, String pagoDepois) {
        Order o = new Order();
        o.setDataPedido(AGORA.minusDays(dias));
        o.setValorTotal(r(valor));
        o.setRecebidoDepois(r(pagoDepois));
        return o;
    }

    @Test
    void compraDentroDoPrazoNaoBloqueia() {
        assertFalse(RegraFiado.estaAtrasada(compra(29, "50", "0"), AGORA));
        assertFalse(RegraFiado.estaAtrasada(compra(30, "50", "0"), AGORA));   // vence hoje: ainda no prazo
        assertEquals(Money.ZERO, RegraFiado.valorEmAtraso(List.of(compra(10, "50", "0")), AGORA));
    }

    @Test
    void compraComMaisDeTrintaDiasBloqueia() {
        assertTrue(RegraFiado.estaAtrasada(compra(31, "50", "0"), AGORA));
        assertEquals(r("50"), RegraFiado.valorEmAtraso(List.of(compra(31, "50", "0")), AGORA));
    }

    @Test
    void soAsComprasAtrasadasEntramNoValorParaDesbloquear() {
        List<Order> abertas = List.of(
                compra(45, "30", "10"),   // atrasada, faltam 20
                compra(35, "15", "0"),    // atrasada, faltam 15
                compra(5, "100", "0"));   // no prazo: não precisa pagar agora
        assertEquals(r("35"), RegraFiado.valorEmAtraso(abertas, AGORA));
    }

    @Test
    void compraAntigaJaQuitadaNaoBloqueia() {
        assertFalse(RegraFiado.estaAtrasada(compra(60, "40", "40"), AGORA));
    }

    @Test
    void pagarOValorEmAtrasoDesbloqueia() {
        // Os pagamentos abatem a compra mais antiga primeiro: pagando os 20 que faltam, quita a atrasada
        Order atrasada = compra(40, "50", "30");
        assertEquals(r("20"), RegraFiado.valorEmAtraso(List.of(atrasada), AGORA));
        atrasada.setRecebidoDepois(r("50"));
        assertEquals(Money.ZERO, RegraFiado.valorEmAtraso(List.of(atrasada), AGORA));
    }

    @Test
    void vencimentoEhTrintaDiasDepoisDaCompra() {
        Order o = new Order();
        o.setDataPedido(LocalDateTime.of(2026, 9, 1, 15, 30));
        assertEquals(LocalDate.of(2026, 10, 1), RegraFiado.vencimento(o));
    }
}
