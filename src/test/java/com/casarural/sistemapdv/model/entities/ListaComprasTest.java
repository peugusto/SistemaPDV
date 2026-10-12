package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Lista de compras da semana. */
class ListaComprasTest {

    private static VendaProduto vendeu(String nome, int vendido, int estoque) {
        Product p = new Product("cod-" + nome, nome, Money.parse("5"), estoque);
        return new VendaProduto(p, vendido, vendido, Money.ZERO, null);
    }

    @Test
    void comprarOQueVendeuMenosOQueTem() {
        assertEquals(20, RegraListaCompras.quantoComprar(30, 10));
        assertEquals(0, RegraListaCompras.quantoComprar(10, 30));   // tem de sobra
        assertEquals(12, RegraListaCompras.quantoComprar(12, -5));  // estoque negativo conta como zero
    }

    @Test
    void soEntraQuemPrecisaRepor() {
        List<RegraListaCompras.Item> lista = RegraListaCompras.montar(List.of(
                vendeu("Arroz", 30, 10),
                vendeu("Feijão", 5, 40),     // tem de sobra
                vendeu("Sal", 0, 0)));       // não vendeu na semana
        assertEquals(1, lista.size());
        assertEquals("Arroz", lista.get(0).getProduto().getNomeProduto());
        assertEquals(20, lista.get(0).getComprar());
    }

    @Test
    void semControleDeEstoqueFicaDeFora() {
        VendaProduto pao = vendeu("Pão", 300, 0);
        pao.getProduto().setControlaEstoque(false);
        VendaProduto queijo = vendeu("Queijo", 20, 0);
        queijo.getProduto().setTipoVenda(TipoVenda.PESAVEL);
        assertTrue(RegraListaCompras.montar(List.of(pao, queijo)).isEmpty());
    }

    @Test
    void osQueAcabaramVemPrimeiro() {
        List<RegraListaCompras.Item> lista = RegraListaCompras.montar(List.of(
                vendeu("Arroz", 30, 10),
                vendeu("Coca", 12, 0)));
        assertEquals("Coca", lista.get(0).getProduto().getNomeProduto());
        assertTrue(lista.get(0).acabou());
    }

    @Test
    void textoParaCopiarSoComQuantidade() {
        List<RegraListaCompras.Item> lista = RegraListaCompras.montar(List.of(
                vendeu("Coca", 12, 0), vendeu("Arroz", 30, 10), vendeu("Óleo", 6, 2)));
        lista.stream().filter(i -> i.getProduto().getNomeProduto().equals("Óleo")).findFirst().orElseThrow().setComprar(0);
        String t = RegraListaCompras.texto(lista, LocalDate.of(2026, 10, 12));
        assertTrue(t.startsWith("LISTA DE COMPRAS - Casa Rural - 12/10/2026"));
        assertTrue(t.contains("[ ] 20 x Arroz"));
        assertTrue(t.contains("[ ] 12 x Coca  (ACABOU)"));
        assertFalse(t.contains("Óleo"));
        assertTrue(t.endsWith("2 produtos"));
    }
}
