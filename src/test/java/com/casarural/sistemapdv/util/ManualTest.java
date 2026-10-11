package com.casarural.sistemapdv.util;

import com.casarural.sistemapdv.util.Manual.Secao;
import com.casarural.sistemapdv.util.Manual.TipoBloco;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Leitura do manual da Ajuda. */
class ManualTest {

    @Test
    void reconheceCadaMarcacao() {
        List<Secao> s = Manual.ler(String.join("\n",
                ";; comentário que não aparece",
                "# Fazer uma venda",
                "Primeira linha do parágrafo",
                "continua aqui.",
                "",
                "## Passos",
                "1. Passe o leitor.",
                "2. Aperte F1.",
                "- um item",
                "! cuidado",
                "> Produto não encontrado!",
                "# Outro assunto",
                "Texto."));
        assertEquals(2, s.size());
        Secao venda = s.get(0);
        assertEquals("Fazer uma venda", venda.getTitulo());
        assertEquals(7, venda.getBlocos().size());
        assertEquals(TipoBloco.PARAGRAFO, venda.getBlocos().get(0).getTipo());
        assertEquals("Primeira linha do parágrafo continua aqui.", venda.getBlocos().get(0).getTexto());
        assertEquals(TipoBloco.SUBTITULO, venda.getBlocos().get(1).getTipo());
        assertEquals(TipoBloco.PASSO, venda.getBlocos().get(2).getTipo());
        assertEquals(2, venda.getBlocos().get(3).getNumero());
        assertEquals("Aperte F1.", venda.getBlocos().get(3).getTexto());
        assertEquals(TipoBloco.ITEM, venda.getBlocos().get(4).getTipo());
        assertEquals(TipoBloco.AVISO, venda.getBlocos().get(5).getTipo());
        assertEquals(TipoBloco.MENSAGEM, venda.getBlocos().get(6).getTipo());
    }

    @Test
    void paragrafoAntesDeUmItemFechaSozinho() {
        List<Secao> s = Manual.ler("# A\nlinha um\n- item");
        assertEquals(TipoBloco.PARAGRAFO, s.get(0).getBlocos().get(0).getTipo());
        assertEquals(TipoBloco.ITEM, s.get(0).getBlocos().get(1).getTipo());
    }

    @Test
    void buscaSemAcentoNoTituloENoTexto() {
        Secao s = Manual.ler("# Pão do dia\nRegistre quantos pães chegaram.").get(0);
        assertTrue(s.contem("pao"));
        assertTrue(s.contem("CHEGARAM paes"));
        assertFalse(s.contem("cigarro"));
        assertTrue(s.contem(""));
    }

    @Test
    void manualDoSistemaCarregaETemProblemasESolucoes() {
        List<Secao> secoes = Manual.carregar();
        assertTrue(secoes.size() >= 10);
        assertTrue(secoes.stream().anyMatch(x -> x.getTitulo().equals("Problemas e soluções")));
        // nenhum assunto vazio
        assertTrue(secoes.stream().allMatch(x -> !x.getBlocos().isEmpty()));
    }
}
