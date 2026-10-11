package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.Periodo;
import com.casarural.sistemapdv.model.entities.PontoVenda;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Contas dos dashboards que não dependem do banco. */
class RelatorioServiceTest {

    // 12/10/2026 é uma segunda-feira
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 12);

    @Test
    void periodosProntos() {
        LocalDate hoje = LocalDate.of(2026, 10, 15);
        assertEquals(1, Periodo.hoje(hoje).quantidadeDeDias());
        assertEquals(LocalDate.of(2026, 10, 9), Periodo.ultimosDias(hoje, 7).getDe());
        assertEquals(LocalDate.of(2026, 10, 1), Periodo.esteMes(hoje).getDe());
        Periodo passado = Periodo.mesPassado(hoje);
        assertEquals(LocalDate.of(2026, 9, 1), passado.getDe());
        assertEquals(LocalDate.of(2026, 9, 30), passado.getAte());
    }

    @Test
    void ultimoDiaEntraInteiro() {
        Periodo p = Periodo.hoje(SEGUNDA);
        assertEquals(SEGUNDA.atStartOfDay(), p.inicio());
        assertEquals(SEGUNDA.plusDays(1).atStartOfDay(), p.fimExclusivo());
    }

    @Test
    void periodoInvertidoERecusado() {
        assertThrows(IllegalArgumentException.class, () -> new Periodo(SEGUNDA, SEGUNDA.minusDays(1)));
    }

    @Test
    void completaOsDiasSemVenda() {
        Map<LocalDate, PontoVenda> vendas = new HashMap<>();
        vendas.put(SEGUNDA.plusDays(1), new PontoVenda("x", 3, Money.parse("30")));
        List<PontoVenda> dias = RelatorioService.completarDias(vendas, new Periodo(SEGUNDA, SEGUNDA.plusDays(2)));
        assertEquals(3, dias.size());
        assertEquals("12/10", dias.get(0).getRotulo());
        assertEquals(0, dias.get(0).getVendas());
        assertEquals(3, dias.get(1).getVendas());
    }

    @Test
    void mediaPorDiaDaSemanaContaOsDiasSemVenda() {
        // duas segundas no período: uma vendeu R$ 100 (4 vendas), a outra nada → média R$ 50 e 2 vendas
        Map<LocalDate, PontoVenda> vendas = new HashMap<>();
        vendas.put(SEGUNDA, new PontoVenda("x", 4, Money.parse("100")));
        Periodo p = new Periodo(SEGUNDA, SEGUNDA.plusWeeks(1));
        List<PontoVenda> media = RelatorioService.mediaPorDiaDaSemana(vendas, p);
        assertEquals(7, media.size());
        PontoVenda segunda = media.get(0);
        assertEquals("seg", segunda.getRotulo());
        assertEquals(Money.parse("50"), segunda.getValor());
        assertEquals(2, segunda.getVendas());
    }

    @Test
    void agrupaPorMesInclusiveMesSemVenda() {
        Map<LocalDate, PontoVenda> vendas = new HashMap<>();
        vendas.put(LocalDate.of(2026, 8, 3), new PontoVenda("x", 2, Money.parse("20")));
        vendas.put(LocalDate.of(2026, 8, 20), new PontoVenda("x", 1, Money.parse("5")));
        vendas.put(LocalDate.of(2026, 10, 1), new PontoVenda("x", 1, Money.parse("7")));
        List<PontoVenda> meses = RelatorioService.porMes(vendas, new Periodo(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 11)));
        assertEquals(3, meses.size());
        assertEquals("ago/26", meses.get(0).getRotulo());
        assertEquals(Money.parse("25"), meses.get(0).getValor());
        assertEquals(3, meses.get(0).getVendas());
        assertEquals(0, meses.get(1).getVendas()); // setembro sem venda
    }

    @Test
    void mediaSoMostraDiasQueExistemNoPeriodo() {
        List<PontoVenda> media = RelatorioService.mediaPorDiaDaSemana(new HashMap<>(), Periodo.hoje(SEGUNDA));
        assertEquals(1, media.size());
    }
}
