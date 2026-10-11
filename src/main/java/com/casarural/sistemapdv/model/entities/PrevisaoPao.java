package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TurnoPao;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Quantos pães comprar. Regra:
 * 1. Pega os últimos 4 dias iguais da semana (ex.: as últimas 4 terças) que tiveram venda ou compra no turno.
 *    Se tiver menos de 2, usa os últimos 7 dias, de qualquer dia da semana.
 * 2. Faz a média da procura desses dias (dias em que acabou contam com a procura estimada, não só o vendido).
 * 3. Arredonda para cima, de 5 em 5.
 * Na tarde, desconta o que sobrou da manhã.
 */
public final class PrevisaoPao {

    static final int DIAS_IGUAIS = 4;
    static final int MINIMO_DIAS_IGUAIS = 2;
    static final int DIAS_RECENTES = 7;
    static final int ARREDONDAR = 5;

    private PrevisaoPao() {}

    /** Resultado: quanto comprar e em quais dias a conta se baseou. */
    public static final class Sugestao {
        private final int procura;
        private final List<DiaPao> base;
        private final boolean mesmoDiaDaSemana;

        Sugestao(int procura, List<DiaPao> base, boolean mesmoDiaDaSemana) {
            this.procura = procura;
            this.base = base;
            this.mesmoDiaDaSemana = mesmoDiaDaSemana;
        }

        /** Quantos pães o turno deve vender, já arredondado. */
        public int getProcura() { return procura; }
        public List<DiaPao> getBase() { return base; }
        public boolean isMesmoDiaDaSemana() { return mesmoDiaDaSemana; }
        public boolean temBase() { return !base.isEmpty(); }

        public long diasQueAcabou(TurnoPao t) {
            return base.stream().filter(d -> d.acabou(t)).count();
        }
    }

    /** Procura prevista para o turno do dia, usando só dias anteriores a ele. */
    public static Sugestao sugerir(List<DiaPao> historico, LocalDate dia, TurnoPao turno) {
        List<DiaPao> anteriores = historico.stream()
                .filter(d -> d.getData().isBefore(dia) && d.temDados(turno))
                .sorted(Comparator.comparing(DiaPao::getData).reversed())
                .collect(Collectors.toList());

        List<DiaPao> iguais = anteriores.stream()
                .filter(d -> d.getData().getDayOfWeek() == dia.getDayOfWeek())
                .limit(DIAS_IGUAIS)
                .collect(Collectors.toList());

        boolean mesmoDia = iguais.size() >= MINIMO_DIAS_IGUAIS;
        List<DiaPao> base = mesmoDia ? iguais : anteriores.stream().limit(DIAS_RECENTES).collect(Collectors.toList());
        if (base.isEmpty()) return new Sugestao(0, base, false);

        double media = base.stream().mapToDouble(d -> d.getProcura(turno)).average().orElse(0);
        return new Sugestao(arredondar(media), base, mesmoDia);
    }

    /** Para a compra das 15h: a procura da tarde menos o que ainda tem da manhã. */
    public static int comprarNaTarde(int procuraTarde, Integer sobraDaManha) {
        int sobra = sobraDaManha == null ? 0 : sobraDaManha;
        return arredondar(Math.max(0, procuraTarde - sobra));
    }

    /**
     * Quanto deve sobrar da manhã na hora da compra das 15h.
     * Antes das 15h a manhã ainda vai vender: desconta o que falta vender pela procura prevista.
     * Ex.: tem 48 agora, a manhã costuma vender 100 e já vendeu 52 → vai vender mais 48 → sobra 0.
     */
    public static Integer sobraPrevistaAs15h(Integer sobraAgora, int vendidoAteAgora, int procuraManha, boolean manhaAcabou) {
        if (sobraAgora == null) return null;
        if (manhaAcabou) return sobraAgora;
        int aindaVaiVender = Math.max(0, procuraManha - vendidoAteAgora);
        return Math.max(0, sobraAgora - aindaVaiVender);
    }

    /** Para cima, de 5 em 5: 41 → 45, 40 → 40, 0 → 0. */
    static int arredondar(double valor) {
        if (valor <= 0) return 0;
        return (int) (Math.ceil(valor / ARREDONDAR) * ARREDONDAR);
    }
}
