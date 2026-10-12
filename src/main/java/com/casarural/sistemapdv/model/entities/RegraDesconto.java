package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Desconto da venda, em R$ ou em %. Sempre vira um valor em R$ (é o que fica gravado). */
public final class RegraDesconto {

    private RegraDesconto() {}

    /**
     * @param subtotal     soma dos itens
     * @param digitado     o que a pessoa digitou ("2", "2,50", "10")
     * @param porcentagem  true = o número é %, false = é R$
     * @return desconto em R$, arredondado em centavos
     * @throws IllegalArgumentException com a mensagem para mostrar na tela
     */
    public static BigDecimal calcular(BigDecimal subtotal, String digitado, boolean porcentagem) {
        BigDecimal numero;
        try {
            numero = Money.parse(digitado);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Digite o desconto, por exemplo 2,00 ou 10.");
        }
        if (numero.signum() < 0) throw new IllegalArgumentException("O desconto não pode ser negativo.");

        BigDecimal desconto;
        if (porcentagem) {
            if (numero.compareTo(BigDecimal.valueOf(100)) >= 0) {
                throw new IllegalArgumentException("A porcentagem tem que ser menor que 100%.");
            }
            desconto = subtotal.multiply(numero).divide(BigDecimal.valueOf(100), Money.SCALE, RoundingMode.HALF_UP);
        } else {
            desconto = Money.of(numero);
        }
        if (desconto.compareTo(subtotal) >= 0) {
            throw new IllegalArgumentException("O desconto (" + Money.format(desconto)
                    + ") não pode ser igual ou maior que a compra (" + Money.format(subtotal) + ").");
        }
        return desconto;
    }

    /** "10%" do subtotal, para mostrar junto do valor: R$ 2,00 (10%). */
    public static String percentual(BigDecimal desconto, BigDecimal subtotal) {
        if (subtotal.signum() == 0) return "0%";
        BigDecimal p = desconto.multiply(BigDecimal.valueOf(100)).divide(subtotal, 1, RoundingMode.HALF_UP);
        String texto = p.stripTrailingZeros().toPlainString().replace('.', ',');
        return texto + "%";
    }
}
