package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Preço de custo e lucro (V14). */
class LucroTest {

    private static BigDecimal r(String v) { return Money.parse(v); }

    private static Product produto(String preco, String custo) {
        Product p = new Product("1", "x", r(preco), 10);
        p.setPrecoCusto(custo == null ? null : r(custo));
        return p;
    }

    @Test
    void itemGuardaOCustoDoMomentoDaVenda() {
        Product arroz = produto("25", "18");
        OrderItem item = new OrderItem(arroz, 2, arroz.getPrecoProduto());
        arroz.setPrecoCusto(r("20"));                    // custo sobe depois
        assertEquals(r("18"), item.getCustoUnitario());  // a venda não muda
    }

    @Test
    void produtoSemCustoFicaSemCusto() {
        assertNull(new OrderItem(produto("25", null), 1, r("25")).getCustoUnitario());
    }

    @Test
    void pesavelCustoProporcionalAoValorDaBalanca() {
        Product queijo = produto("50", "30");             // preço e custo do kg
        queijo.setTipoVenda(TipoVenda.PESAVEL);
        assertEquals(r("7,44"), RegraLucro.custoDoItem(queijo, r("12,40")));
    }

    @Test
    void margemSobreAVenda() {
        assertEquals(r("7"), RegraLucro.lucro(r("25"), r("18")));
        assertEquals("28%", RegraLucro.formatarMargem(RegraLucro.margem(r("25"), r("18"))));
        assertEquals("—", RegraLucro.formatarMargem(RegraLucro.margem(r("0"), r("0"))));
    }

    @Test
    void lucroDoPeriodoSoContaVendasComCustoEDescontaODescontoProporcional() {
        ResumoVendas rv = new ResumoVendas();
        rv.setReceitaItens(r("200"));     // vendeu R$ 200 em itens
        rv.setReceitaComCusto(r("150"));  // R$ 150 deles têm custo
        rv.setCustoItens(r("100"));
        rv.setDescontos(r("8"));          // descontos do período
        assertEquals(0, rv.getCoberturaCusto().compareTo(BigDecimal.valueOf(75)));
        assertEquals(r("6"), rv.getDescontoComCusto());         // 8 x 150/200
        assertEquals(r("44"), rv.getLucroBruto());              // 150 - 6 - 100
        assertEquals("30,6%", RegraLucro.formatarMargem(rv.getMargem()));  // 44 / 144
    }

    @Test
    void semVendasNaoTemCobertura() {
        assertNull(new ResumoVendas().getCoberturaCusto());
        assertEquals(r("0"), new ResumoVendas().getLucroBruto());
    }
}
