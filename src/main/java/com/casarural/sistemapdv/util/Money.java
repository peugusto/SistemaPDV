package com.casarural.sistemapdv.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Regras únicas para valores em dinheiro (R$).
 *
 * Todo valor monetário do sistema é BigDecimal com 2 casas decimais, arredondado com HALF_UP
 * (o arredondamento "de escola": 0,005 vira 0,01). Nunca use double para dinheiro:
 * double não representa 0,10 exatamente, e os erros se acumulam em somas e subtrações.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private static final DecimalFormatSymbols PT_BR = DecimalFormatSymbols.getInstance(new Locale("pt", "BR"));

    private Money() {}

    /** Normaliza para 2 casas. Null vira zero. */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    /** Converte o texto digitado pelo usuário: aceita "5", "5,5", "5.50", "1.234,56" e "R$ 5,00". */
    public static BigDecimal parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new NumberFormatException("Valor vazio.");
        }
        String s = text.replace("R$", "").replace(" ", "").trim();
        if (s.contains(",")) {
            // formato brasileiro: ponto é milhar e vírgula é decimal
            s = s.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(s).setScale(SCALE, ROUNDING);
    }

    /** "R$ 1.234,56" — para exibir ao usuário. */
    public static String format(BigDecimal value) {
        return "R$ " + formatPlain(value);
    }

    /** "1.234,56" — sem o símbolo. */
    public static String formatPlain(BigDecimal value) {
        return new DecimalFormat("#,##0.00", PT_BR).format(of(value));
    }

    /** "1234,56" — para preencher campos de edição (sem separador de milhar). */
    public static String toInput(BigDecimal value) {
        return new DecimalFormat("0.00", PT_BR).format(of(value));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
