package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TurnoPao;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Um dia do pão: quanto chegou e quanto vendeu em cada turno.
 * Montado pelo relatório a partir de compra_pao (V12) e das vendas do produto.
 */
public class DiaPao {

    /** Se o pão acabou, o turno é "esticado" até no máximo este fator (evita exagero quando acaba logo cedo). */
    static final double FATOR_MAXIMO = 2.0;
    /** Menos que isso de venda antes de acabar não dá para medir o ritmo direito. */
    static final long MINUTOS_MINIMOS = 30;

    private final LocalDate data;
    private final Map<TurnoPao, Integer> comprado = new EnumMap<>(TurnoPao.class);
    private final Map<TurnoPao, Integer> vendido = new EnumMap<>(TurnoPao.class);
    private final Map<TurnoPao, LocalTime> ultimaVenda = new EnumMap<>(TurnoPao.class);

    public DiaPao(LocalDate data) {
        this.data = data;
    }

    public LocalDate getData() { return data; }

    /** null = ninguém registrou quantos chegaram nesse turno. */
    public Integer getComprado(TurnoPao t) { return comprado.get(t); }
    public void setComprado(TurnoPao t, Integer quantidade) { comprado.put(t, quantidade); }

    public int getVendido(TurnoPao t) { return vendido.getOrDefault(t, 0); }
    public LocalTime getUltimaVenda(TurnoPao t) { return ultimaVenda.get(t); }

    public void setVendas(TurnoPao t, int quantidade, LocalTime ultima) {
        vendido.put(t, quantidade);
        ultimaVenda.put(t, ultima);
    }

    public int getVendidoNoDia() {
        return getVendido(TurnoPao.MANHA) + getVendido(TurnoPao.TARDE);
    }

    /**
     * Pães à venda no turno. Manhã: o que chegou às 7h. Tarde: a sobra da manhã + o que chegou às 15h.
     * null quando falta algum registro de compra (aí não dá para saber se acabou).
     */
    public Integer getDisponivel(TurnoPao t) {
        Integer manha = getComprado(TurnoPao.MANHA);
        if (t == TurnoPao.MANHA) return manha;
        Integer tarde = getComprado(TurnoPao.TARDE);
        if (manha == null || tarde == null) return null;
        return getSobra(TurnoPao.MANHA) + tarde;
    }

    /** O que sobrou no fim do turno (ou até agora, se o turno ainda está aberto). null se não sabe. */
    public Integer getSobra(TurnoPao t) {
        Integer disponivel = getDisponivel(t);
        return disponivel == null ? null : Math.max(0, disponivel - getVendido(t));
    }

    /** Vendeu tudo o que tinha: a procura pode ter sido maior do que a venda. */
    public boolean acabou(TurnoPao t) {
        Integer disponivel = getDisponivel(t);
        return disponivel != null && disponivel > 0 && getVendido(t) >= disponivel;
    }

    public boolean temDados(TurnoPao t) {
        return getVendido(t) > 0 || getComprado(t) != null;
    }

    /**
     * Quantos pães o turno teria vendido se não faltasse.
     * Se não acabou, é o que vendeu. Se acabou, estica o ritmo de venda até o fim do turno:
     * vendeu 60 até as 11h numa manhã de 8 horas (4 horas de venda) → procura estimada de 120.
     */
    public double getProcura(TurnoPao t) {
        int vendidos = getVendido(t);
        LocalTime ultima = getUltimaVenda(t);
        if (!acabou(t) || ultima == null) return vendidos;

        long minutosVendendo = Duration.between(t.getInicio(), ultima).toMinutes();
        minutosVendendo = Math.max(MINUTOS_MINIMOS, minutosVendendo);
        double fator = (double) t.minutos() / minutosVendendo;
        fator = Math.max(1.0, Math.min(FATOR_MAXIMO, fator));
        return vendidos * fator;
    }
}
