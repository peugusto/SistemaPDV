package com.casarural.sistemapdv.model.entities.enums;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Os dois horários em que o pão chega. Coluna compra_pao.turno (V12).
 * Venda antes das 15h conta na manhã (inclusive antes das 7h); das 15h em diante, na tarde.
 */
public enum TurnoPao {
    MANHA("Manhã", LocalTime.of(7, 0), LocalTime.of(15, 0)),
    TARDE("Tarde", LocalTime.of(15, 0), LocalTime.of(19, 0));

    private final String nome;
    private final LocalTime inicio;
    private final LocalTime fim;

    TurnoPao(String nome, LocalTime inicio, LocalTime fim) {
        this.nome = nome;
        this.inicio = inicio;
        this.fim = fim;
    }

    public String getNome() { return nome; }
    public LocalTime getInicio() { return inicio; }
    public LocalTime getFim() { return fim; }

    public long minutos() { return Duration.between(inicio, fim).toMinutes(); }

    /** "Manhã (7h–15h)" */
    public String descricao() {
        return String.format("%s (%dh–%dh)", nome, inicio.getHour(), fim.getHour());
    }

    public static TurnoPao de(LocalTime hora) {
        return hora.isBefore(TARDE.inicio) ? MANHA : TARDE;
    }

    @Override public String toString() { return nome; }
}
