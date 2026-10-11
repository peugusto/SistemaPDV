package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.services.BuscaProduto.Filtro;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Busca de produto do F2 no PDV. */
class BuscaProdutoTest {

    private static Product produto(String codigo, String nome, int estoque) {
        return new Product(codigo, nome, Money.parse("5,00"), estoque);
    }

    private static Product pesavel(String codigo, String nome) {
        Product p = produto(codigo, nome, 0);
        p.setTipoVenda(TipoVenda.PESAVEL);
        return p;
    }

    private static Product semControle(String codigo, String nome) {
        Product p = produto(codigo, nome, -3);
        p.setControlaEstoque(false);
        return p;
    }

    @Test
    void ignoraAcentoEMaiuscula() {
        Product acai = semControle("21", "Açaí 500 ml");
        assertTrue(BuscaProduto.combina(acai, "ACAI", Filtro.TODOS, false));
        assertTrue(BuscaProduto.combina(acai, "açaí", Filtro.TODOS, false));
    }

    @Test
    void todasAsPalavrasPrecisamAparecer() {
        Product acai500 = semControle("21", "Açaí 500 ml");
        Product acai300 = semControle("20", "Açaí 300 ml");
        assertTrue(BuscaProduto.combina(acai500, "acai 500", Filtro.TODOS, false));
        assertFalse(BuscaProduto.combina(acai300, "acai 500", Filtro.TODOS, false));
    }

    @Test
    void achaPeloCodigo() {
        Product coca = produto("7894900011517", "Coca-Cola 2L", 10);
        assertTrue(BuscaProduto.combina(coca, "789490", Filtro.TODOS, false));
    }

    @Test
    void buscaVaziaMostraTudo() {
        assertTrue(BuscaProduto.combina(produto("1", "Pão francês", 0), "  ", Filtro.TODOS, false));
        assertTrue(BuscaProduto.combina(produto("1", "Pão francês", 0), null, Filtro.TODOS, false));
    }

    @Test
    void filtroPesaveis() {
        assertTrue(BuscaProduto.combina(pesavel("10", "Queijo"), "", Filtro.PESAVEIS, false));
        assertFalse(BuscaProduto.combina(produto("123456789", "Arroz", 5), "", Filtro.PESAVEIS, false));
    }

    @Test
    void filtroSemControleIncluiPesaveis() {
        assertTrue(BuscaProduto.combina(semControle("1", "Pão"), "", Filtro.SEM_ESTOQUE_CONTROLADO, false));
        assertTrue(BuscaProduto.combina(pesavel("10", "Queijo"), "", Filtro.SEM_ESTOQUE_CONTROLADO, false));
        assertFalse(BuscaProduto.combina(produto("123456789", "Arroz", 5), "", Filtro.SEM_ESTOQUE_CONTROLADO, false));
    }

    @Test
    void filtroCodigosCurtos() {
        assertTrue(BuscaProduto.combina(semControle("21", "Açaí"), "", Filtro.CODIGOS_CURTOS, false));
        assertTrue(BuscaProduto.combina(semControle("1000", "Pão"), "", Filtro.CODIGOS_CURTOS, false));
        assertFalse(BuscaProduto.combina(produto("12345", "Arroz", 5), "", Filtro.CODIGOS_CURTOS, false));
    }

    @Test
    void soComEstoqueEscondeZeradoMasNaoPesavelNemSemControle() {
        assertFalse(BuscaProduto.combina(produto("123456789", "Arroz", 0), "", Filtro.TODOS, true));
        assertTrue(BuscaProduto.combina(produto("123456789", "Arroz", 2), "", Filtro.TODOS, true));
        assertTrue(BuscaProduto.combina(pesavel("10", "Queijo"), "", Filtro.TODOS, true));
        assertTrue(BuscaProduto.combina(semControle("1", "Pão"), "", Filtro.TODOS, true));
    }
}
