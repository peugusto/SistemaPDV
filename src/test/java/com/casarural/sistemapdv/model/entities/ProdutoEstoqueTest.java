package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Produtos pesáveis e produtos sem controle de estoque (V11). */
class ProdutoEstoqueTest {

    private static Product produto(int estoque) {
        return new Product("cod", "produto", Money.parse("10,00"), estoque);
    }

    @Test
    void produtoNovoVendePorUnidadeEControlaEstoque() {
        Product p = produto(5);
        assertEquals(TipoVenda.UNIDADE, p.getTipoVenda());
        assertFalse(p.isPesavel());
        assertTrue(p.isControlaEstoque());
    }

    @Test
    void comControleNaoVendeMaisQueOEstoque() {
        Product p = produto(5);
        assertTrue(p.temEstoquePara(5));
        assertFalse(p.temEstoquePara(6));
    }

    @Test
    void semControleVendeMesmoComEstoqueNegativo() {
        // caso do pão hoje: estoque -17 travava a venda
        Product pao = produto(-17);
        pao.setControlaEstoque(false);
        assertTrue(pao.temEstoquePara(30));
    }

    @Test
    void pesavelNuncaControlaEstoque() {
        Product queijo = produto(0);
        queijo.setTipoVenda(TipoVenda.PESAVEL);
        queijo.setControlaEstoque(true); // mesmo marcado, pesável ignora
        assertTrue(queijo.isPesavel());
        assertFalse(queijo.isControlaEstoque());
        assertTrue(queijo.temEstoquePara(1));
    }

    @Test
    void tipoNuloViraUnidade() {
        Product p = produto(1);
        p.setTipoVenda(null);
        assertEquals(TipoVenda.UNIDADE, p.getTipoVenda());
    }

    @Test
    void itemPesavelVaiComOValorDaBalanca() {
        Product queijo = produto(0);
        queijo.setTipoVenda(TipoVenda.PESAVEL);
        OrderItem item = new OrderItem(queijo, 1, Money.parse("12,40"));
        Order venda = new Order();
        venda.setItemPedido(List.of(item));
        assertEquals(Money.parse("12,40"), item.getSubtotal());
    }
}
