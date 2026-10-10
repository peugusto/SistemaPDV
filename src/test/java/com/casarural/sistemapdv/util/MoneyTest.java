package com.casarural.sistemapdv.util;

import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void parseAceitaFormatosDigitadosPeloUsuario() {
        assertEquals(bd("5.00"), Money.parse("5"));
        assertEquals(bd("5.50"), Money.parse("5,5"));
        assertEquals(bd("5.50"), Money.parse("5.50"));
        assertEquals(bd("1234.56"), Money.parse("1.234,56"));
        assertEquals(bd("10.00"), Money.parse("R$ 10,00"));
        assertEquals(bd("0.50"), Money.parse(",5"));
    }

    @Test
    void parseRejeitaTextoInvalido() {
        assertThrows(NumberFormatException.class, () -> Money.parse(""));
        assertThrows(NumberFormatException.class, () -> Money.parse("   "));
        assertThrows(NumberFormatException.class, () -> Money.parse("abc"));
        assertThrows(NumberFormatException.class, () -> Money.parse(null));
    }

    @Test
    void arredondaParaDuasCasasComHalfUp() {
        assertEquals(bd("0.01"), Money.of(bd("0.005")));
        assertEquals(bd("2.68"), Money.of(bd("2.675")));
        assertEquals(Money.ZERO, Money.of(null));
    }

    @Test
    void formataNoPadraoBrasileiro() {
        assertEquals("R$ 1.234,56", Money.format(bd("1234.56")));
        assertEquals("R$ 0,00", Money.format(null));
        assertEquals("1234,50", Money.toInput(bd("1234.5")));
    }

    @Test
    void somaDeCentavosEhExata() {
        // Com double: 0.1 + 0.2 = 0.30000000000000004
        assertNotEquals(0.3, 0.1 + 0.2);
        assertEquals(bd("0.30"), Money.parse("0,10").add(Money.parse("0,20")));

        // 10 itens de R$ 0,10 somam exatamente R$ 1,00
        BigDecimal total = Money.ZERO;
        for (int i = 0; i < 10; i++) total = total.add(Money.parse("0,10"));
        assertEquals(0, total.compareTo(bd("1.00")));
    }

    @Test
    void subtotalDoItemEhPrecoVezesQuantidade() {
        Product p = new Product("123", "Pão", Money.parse("0,35"), 100);
        OrderItem item = new OrderItem(p, 3, p.getPrecoProduto());
        assertEquals(bd("1.05"), item.getSubtotal());
    }

    @Test
    void pagamentoMistoZeraOSaldoExatamente() {
        // Venda de R$ 18,30 paga em 3 partes: antes precisava de "< 0.009" para considerar zerado
        BigDecimal saldo = Money.parse("18,30");
        saldo = saldo.subtract(Money.parse("10,10"));
        saldo = saldo.subtract(Money.parse("5,10"));
        saldo = saldo.subtract(Money.parse("3,10"));
        assertEquals(0, saldo.signum());
    }
}
