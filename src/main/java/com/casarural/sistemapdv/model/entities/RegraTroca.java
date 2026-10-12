package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.Order.PaymentSlice;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Troca: o que o cliente já pagou na venda antiga vira crédito na venda nova, nas mesmas formas.
 *
 * Se a venda nova custa menos que o crédito, sobra crédito para devolver. A sobra sai nesta ordem:
 * 1. fiado (só diminui a dívida, nada para devolver na mão);
 * 2. dinheiro (devolve da gaveta);
 * 3. PIX e cartão (devolve por PIX / estorno na maquininha; raro).
 */
public final class RegraTroca {

    /** Ordem em que o crédito que sobra é devolvido. */
    private static final PaymentMethod[] ORDEM_DEVOLUCAO = {
            PaymentMethod.FIADO, PaymentMethod.DINHEIRO, PaymentMethod.PIX, PaymentMethod.CARTAO};
    /** Ordem em que o crédito aparece no pagamento. */
    private static final PaymentMethod[] ORDEM_CREDITO = {
            PaymentMethod.DINHEIRO, PaymentMethod.PIX, PaymentMethod.CARTAO, PaymentMethod.FIADO};

    private RegraTroca() {}

    public static final class Resultado {
        private final List<PaymentSlice> creditos;
        private final Map<PaymentMethod, BigDecimal> devolver;

        Resultado(List<PaymentSlice> creditos, Map<PaymentMethod, BigDecimal> devolver) {
            this.creditos = creditos;
            this.devolver = devolver;
        }

        /** Fatias de crédito para pôr no pagamento da venda nova. */
        public List<PaymentSlice> getCreditos() { return creditos; }

        /** O que devolver ao cliente, por forma (sem o fiado: esse só sai da conta). */
        public Map<PaymentMethod, BigDecimal> getDevolver() { return devolver; }

        public BigDecimal getTotalCredito() {
            return creditos.stream().map(PaymentSlice::getValor).reduce(Money.ZERO, BigDecimal::add);
        }

        public BigDecimal getTotalDevolver() {
            return devolver.values().stream().reduce(Money.ZERO, BigDecimal::add);
        }
    }

    /**
     * @param pagoAntes o que a venda antiga teve em cada forma (dinheiro, PIX, cartão e o que ficou no fiado)
     * @param novoTotal total da venda nova
     */
    public static Resultado calcular(Map<PaymentMethod, BigDecimal> pagoAntes, BigDecimal novoTotal) {
        Map<PaymentMethod, BigDecimal> credito = new EnumMap<>(PaymentMethod.class);
        BigDecimal soma = Money.ZERO;
        for (PaymentMethod m : ORDEM_CREDITO) {
            BigDecimal v = Money.of(pagoAntes.get(m));
            if (Money.isPositive(v)) {
                credito.put(m, v);
                soma = soma.add(v);
            }
        }

        // Crédito maior que a compra nova: tira a sobra na ordem de devolução
        Map<PaymentMethod, BigDecimal> devolver = new EnumMap<>(PaymentMethod.class);
        BigDecimal sobra = Money.max(Money.ZERO, soma.subtract(Money.of(novoTotal)));
        for (PaymentMethod m : ORDEM_DEVOLUCAO) {
            if (!Money.isPositive(sobra)) break;
            BigDecimal v = credito.getOrDefault(m, Money.ZERO);
            BigDecimal tira = Money.min(v, sobra);
            if (!Money.isPositive(tira)) continue;
            credito.put(m, v.subtract(tira));
            sobra = sobra.subtract(tira);
            if (m != PaymentMethod.FIADO) devolver.put(m, tira);
        }

        List<PaymentSlice> fatias = new ArrayList<>();
        for (PaymentMethod m : ORDEM_CREDITO) {
            BigDecimal v = credito.getOrDefault(m, Money.ZERO);
            if (Money.isPositive(v)) fatias.add(new PaymentSlice(v, m, true));
        }
        return new Resultado(fatias, devolver);
    }

    /** "R$ 2,00 em dinheiro e R$ 1,00 por PIX" */
    public static String descreverDevolucao(Map<PaymentMethod, BigDecimal> devolver) {
        List<String> partes = new ArrayList<>();
        for (Map.Entry<PaymentMethod, BigDecimal> e : devolver.entrySet()) {
            partes.add(Money.format(e.getValue()) + " " + comoDevolver(e.getKey()));
        }
        return String.join(" e ", partes);
    }

    /** "em dinheiro", "por PIX", "no cartão (estorno na maquininha)" */
    public static String comoDevolver(PaymentMethod m) {
        switch (m) {
            case DINHEIRO: return "em dinheiro";
            case PIX: return "por PIX";
            case CARTAO: return "no cartão (estorno na maquininha)";
            default: return "no fiado";
        }
    }
}
