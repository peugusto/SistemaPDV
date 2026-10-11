package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TurnoPao;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.casarural.sistemapdv.model.entities.enums.TurnoPao.MANHA;
import static com.casarural.sistemapdv.model.entities.enums.TurnoPao.TARDE;
import static org.junit.jupiter.api.Assertions.*;

/** Cálculo de quantos pães comprar. */
class PrevisaoPaoTest {

    // 13/10/2026 é uma terça-feira
    private static final LocalDate TERCA = LocalDate.of(2026, 10, 13);

    private static DiaPao dia(LocalDate data, Integer compradoManha, int vendidoManha, String ultimaManha) {
        DiaPao d = new DiaPao(data);
        d.setComprado(MANHA, compradoManha);
        d.setVendas(MANHA, vendidoManha, ultimaManha == null ? null : LocalTime.parse(ultimaManha));
        return d;
    }

    @Test
    void naoAcabouProcuraEOQueVendeu() {
        DiaPao d = dia(TERCA, 100, 80, "14:50");
        assertFalse(d.acabou(MANHA));
        assertEquals(20, d.getSobra(MANHA));
        assertEquals(80, d.getProcura(MANHA), 0.001);
    }

    @Test
    void acabouCedoEstimaAProcuraPeloRitmo() {
        // 60 pães das 7h às 11h (4 de 8 horas): procura estimada de 120
        DiaPao d = dia(TERCA, 60, 60, "11:00");
        assertTrue(d.acabou(MANHA));
        assertEquals(0, d.getSobra(MANHA));
        assertEquals(120, d.getProcura(MANHA), 0.001);
    }

    @Test
    void estimativaTemTetoQuandoAcabaLogoCedo() {
        // acabou às 8h: o fator seria 8x, mas fica limitado a 2x
        DiaPao d = dia(TERCA, 50, 50, "08:00");
        assertEquals(100, d.getProcura(MANHA), 0.001);
    }

    @Test
    void semRegistroDeCompraNaoDaParaSaberSeAcabou() {
        DiaPao d = dia(TERCA, null, 70, "10:00");
        assertFalse(d.acabou(MANHA));
        assertNull(d.getSobra(MANHA));
        assertEquals(70, d.getProcura(MANHA), 0.001);
    }

    @Test
    void tardeSomaASobraDaManha() {
        DiaPao d = dia(TERCA, 100, 90, "14:30");
        d.setComprado(TARDE, 30);
        d.setVendas(TARDE, 35, LocalTime.of(18, 40));
        assertEquals(40, d.getDisponivel(TARDE)); // 10 da manhã + 30
        assertEquals(5, d.getSobra(TARDE));
        assertFalse(d.acabou(TARDE));
    }

    @Test
    void usaOsMesmosDiasDaSemana() {
        List<DiaPao> h = new ArrayList<>();
        h.add(dia(TERCA.minusWeeks(1), 100, 80, "14:00"));
        h.add(dia(TERCA.minusWeeks(2), 100, 90, "14:00"));
        h.add(dia(TERCA.minusDays(1), 200, 200, "14:59")); // segunda, não entra
        PrevisaoPao.Sugestao s = PrevisaoPao.sugerir(h, TERCA, MANHA);
        assertTrue(s.isMesmoDiaDaSemana());
        assertEquals(2, s.getBase().size());
        assertEquals(85, s.getProcura()); // média 85
    }

    @Test
    void ignoraOProprioDiaEDiasDepois() {
        List<DiaPao> h = new ArrayList<>();
        h.add(dia(TERCA.minusWeeks(1), 100, 80, "14:00"));
        h.add(dia(TERCA.minusWeeks(2), 100, 80, "14:00"));
        h.add(dia(TERCA, 500, 500, "14:00"));
        h.add(dia(TERCA.plusWeeks(1), 500, 500, "14:00"));
        assertEquals(80, PrevisaoPao.sugerir(h, TERCA, MANHA).getProcura());
    }

    @Test
    void poucosDiasIguaisUsaOsUltimos7Dias() {
        List<DiaPao> h = new ArrayList<>();
        h.add(dia(TERCA.minusWeeks(1), 100, 61, "14:00")); // só uma terça
        h.add(dia(TERCA.minusDays(1), 100, 70, "14:00"));
        h.add(dia(TERCA.minusDays(2), 100, 70, "14:00"));
        PrevisaoPao.Sugestao s = PrevisaoPao.sugerir(h, TERCA, MANHA);
        assertFalse(s.isMesmoDiaDaSemana());
        assertEquals(3, s.getBase().size());
        assertEquals(70, s.getProcura()); // média 67 → 70
    }

    @Test
    void diaQueAcabouPuxaASugestaoParaCima() {
        List<DiaPao> h = new ArrayList<>();
        h.add(dia(TERCA.minusWeeks(1), 80, 80, "11:00")); // acabou: procura 160
        h.add(dia(TERCA.minusWeeks(2), 100, 80, "14:00"));
        PrevisaoPao.Sugestao s = PrevisaoPao.sugerir(h, TERCA, MANHA);
        assertEquals(120, s.getProcura()); // (160 + 80) / 2
        assertEquals(1, s.diasQueAcabou(MANHA));
    }

    @Test
    void semHistoricoNaoSugere() {
        PrevisaoPao.Sugestao s = PrevisaoPao.sugerir(List.of(), TERCA, MANHA);
        assertFalse(s.temBase());
        assertEquals(0, s.getProcura());
    }

    @Test
    void compraDaTardeDescontaASobra() {
        assertEquals(30, PrevisaoPao.comprarNaTarde(45, 15));
        assertEquals(0, PrevisaoPao.comprarNaTarde(20, 30));
        assertEquals(45, PrevisaoPao.comprarNaTarde(45, null));
    }

    @Test
    void sobraDas15hDescontaOQueAManhaAindaVaiVender() {
        // 11h: tem 48, a manhã costuma vender 100 e já vendeu 52 → não deve sobrar nada
        assertEquals(0, PrevisaoPao.sobraPrevistaAs15h(48, 52, 100, false));
        // manhã fraca: costuma vender 60, já vendeu 52 → vende mais 8 → sobram 40
        assertEquals(40, PrevisaoPao.sobraPrevistaAs15h(48, 52, 60, false));
        // depois das 15h vale a sobra de verdade
        assertEquals(48, PrevisaoPao.sobraPrevistaAs15h(48, 52, 100, true));
        assertNull(PrevisaoPao.sobraPrevistaAs15h(null, 52, 100, false));
    }

    @Test
    void arredondaDe5Em5ParaCima() {
        assertEquals(45, PrevisaoPao.arredondar(41));
        assertEquals(40, PrevisaoPao.arredondar(40));
        assertEquals(0, PrevisaoPao.arredondar(0));
    }

    @Test
    void turnoPelaHoraDaVenda() {
        assertEquals(MANHA, TurnoPao.de(LocalTime.of(6, 30)));
        assertEquals(MANHA, TurnoPao.de(LocalTime.of(14, 59)));
        assertEquals(TARDE, TurnoPao.de(LocalTime.of(15, 0)));
        assertEquals(TARDE, TurnoPao.de(LocalTime.of(19, 30)));
    }
}
