package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.RelatorioDao;
import com.casarural.sistemapdv.model.entities.Periodo;
import com.casarural.sistemapdv.model.entities.PontoVenda;
import com.casarural.sistemapdv.model.entities.ResumoVendas;
import com.casarural.sistemapdv.model.entities.VendaProduto;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RelatorioService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final RelatorioDao dao;

    public RelatorioService(RelatorioDao dao) {
        this.dao = dao;
    }

    public ResumoVendas resumo(Periodo p) {
        return dao.resumo(p.inicio(), p.fimExclusivo());
    }

    /** Vendas por dia, só dos dias que venderam. Passe para completarDias e mediaPorDiaDaSemana. */
    public Map<LocalDate, PontoVenda> porDia(Periodo p) {
        return dao.vendasPorDia(p.inicio(), p.fimExclusivo());
    }

    /** Um ponto por hora, da primeira à última hora com venda. */
    public List<PontoVenda> porHora(Periodo p) {
        Map<Integer, PontoVenda> horas = dao.vendasPorHora(p.inicio(), p.fimExclusivo());
        List<PontoVenda> lista = new ArrayList<>();
        if (horas.isEmpty()) return lista;
        int primeira = horas.keySet().stream().min(Integer::compare).orElse(0);
        int ultima = horas.keySet().stream().max(Integer::compare).orElse(23);
        for (int h = primeira; h <= ultima; h++) {
            lista.add(horas.getOrDefault(h, new PontoVenda(h + "h")));
        }
        return lista;
    }

    public List<VendaProduto> porProduto(Periodo p) {
        return dao.vendasPorProduto(p.inicio(), p.fimExclusivo());
    }

    /** Um ponto por dia do período, inclusive dias sem venda (aparecem como zero no gráfico). */
    public static List<PontoVenda> completarDias(Map<LocalDate, PontoVenda> vendas, Periodo p) {
        List<PontoVenda> lista = new ArrayList<>();
        for (LocalDate d = p.getDe(); !d.isAfter(p.getAte()); d = d.plusDays(1)) {
            PontoVenda v = vendas.get(d);
            lista.add(new PontoVenda(d.format(DIA_MES), v == null ? 0 : v.getVendas(), v == null ? Money.ZERO : v.getValor()));
        }
        return lista;
    }

    /** Acima disso o gráfico "por dia" fica ilegível: agrupa por mês. */
    public static final int MAXIMO_DIAS_NO_GRAFICO = 62;

    /** Soma os dias em meses ("out/26"), na ordem do período, inclusive meses sem venda. */
    public static List<PontoVenda> porMes(Map<LocalDate, PontoVenda> vendas, Periodo p) {
        DateTimeFormatter mes = DateTimeFormatter.ofPattern("MMM/yy", PT_BR);
        Map<String, PontoVenda> meses = new java.util.LinkedHashMap<>();
        for (LocalDate d = p.getDe(); !d.isAfter(p.getAte()); d = d.plusDays(1)) {
            String rotulo = d.format(mes).replace(".", "");
            PontoVenda m = meses.computeIfAbsent(rotulo, PontoVenda::new);
            PontoVenda v = vendas.get(d);
            if (v != null) m.somar(v.getVendas(), v.getValor());
        }
        return new ArrayList<>(meses.values());
    }

    /**
     * Média de faturamento e de vendas por dia da semana (segunda a domingo).
     * Divide pelo número de vezes que aquele dia aparece no período, contando os dias sem venda:
     * "uma terça normal vende R$ X".
     */
    public static List<PontoVenda> mediaPorDiaDaSemana(Map<LocalDate, PontoVenda> vendas, Periodo p) {
        Map<DayOfWeek, int[]> ocorrencias = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, PontoVenda> soma = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            ocorrencias.put(d, new int[1]);
            soma.put(d, new PontoVenda(d.name()));
        }
        for (LocalDate d = p.getDe(); !d.isAfter(p.getAte()); d = d.plusDays(1)) {
            ocorrencias.get(d.getDayOfWeek())[0]++;
            PontoVenda v = vendas.get(d);
            if (v != null) soma.get(d.getDayOfWeek()).somar(v.getVendas(), v.getValor());
        }

        List<PontoVenda> lista = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            int vezes = ocorrencias.get(d)[0];
            if (vezes == 0) continue;
            PontoVenda s = soma.get(d);
            String nome = d.getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
            lista.add(new PontoVenda(nome,
                    Math.round((float) s.getVendas() / vezes),
                    s.getValor().divide(BigDecimal.valueOf(vezes), Money.SCALE, RoundingMode.HALF_UP)));
        }
        return lista;
    }
}
