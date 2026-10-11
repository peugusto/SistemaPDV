package com.casarural.sistemapdv.model.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Período dos relatórios, de um dia até outro, os dois inclusive. */
public final class Periodo {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LocalDate de;
    private final LocalDate ate;

    public Periodo(LocalDate de, LocalDate ate) {
        if (de == null || ate == null) throw new IllegalArgumentException("Escolha as duas datas.");
        if (ate.isBefore(de)) throw new IllegalArgumentException("A data final é antes da inicial.");
        this.de = de;
        this.ate = ate;
    }

    public static Periodo hoje(LocalDate hoje) { return new Periodo(hoje, hoje); }
    public static Periodo ultimosDias(LocalDate hoje, int dias) { return new Periodo(hoje.minusDays(dias - 1L), hoje); }
    public static Periodo esteMes(LocalDate hoje) { return new Periodo(hoje.withDayOfMonth(1), hoje); }

    public static Periodo mesPassado(LocalDate hoje) {
        LocalDate inicio = hoje.withDayOfMonth(1).minusMonths(1);
        return new Periodo(inicio, inicio.withDayOfMonth(inicio.lengthOfMonth()));
    }

    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }

    /** Para o SQL: início do primeiro dia. */
    public LocalDateTime inicio() { return de.atStartOfDay(); }
    /** Para o SQL: início do dia seguinte ao último (o último dia entra inteiro). */
    public LocalDateTime fimExclusivo() { return ate.plusDays(1).atStartOfDay(); }

    public long quantidadeDeDias() { return ChronoUnit.DAYS.between(de, ate) + 1; }

    public boolean umDiaSo() { return de.equals(ate); }

    public String descricao() {
        return umDiaSo() ? de.format(DATA) : de.format(DATA) + " a " + ate.format(DATA);
    }
}
