package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Custo e lucro. O custo de cada item é gravado na venda (itempedido.custo_unitario, V14)
 * para o lucro do passado não mudar quando o custo do produto mudar.
 */
public final class RegraLucro {

    private RegraLucro() {}

    /**
     * Custo de um item no momento da venda (por unidade vendida); null se o produto não tem custo.
     * Pesável: o caixa digita o valor, não o peso. O custo sai pela proporção do preço do kg:
     * custo/kg R$ 30, preço/kg R$ 50, venda de R$ 12,40 → custo R$ 7,44 (mesma margem do kg).
     */
    public static BigDecimal custoDoItem(Product p, BigDecimal precoCobrado) {
        BigDecimal custo = p.getPrecoCusto();
        if (custo == null) return null;
        if (!p.isPesavel()) return Money.of(custo);
        BigDecimal precoKg = p.getPrecoProduto();
        if (precoKg == null || precoKg.signum() <= 0) return null;
        return Money.of(precoCobrado).multiply(custo).divide(precoKg, Money.SCALE, RoundingMode.HALF_UP);
    }

    /** Lucro = venda - custo. */
    public static BigDecimal lucro(BigDecimal venda, BigDecimal custo) {
        return Money.of(venda).subtract(Money.of(custo));
    }

    /** Margem sobre a venda, em % (lucro / venda). null se a venda for zero. */
    public static BigDecimal margem(BigDecimal venda, BigDecimal custo) {
        if (venda == null || venda.signum() == 0) return null;
        return lucro(venda, custo).multiply(BigDecimal.valueOf(100)).divide(venda, 1, RoundingMode.HALF_UP);
    }

    /** "32,5%"; "—" sem margem. */
    public static String formatarMargem(BigDecimal margem) {
        if (margem == null) return "—";
        return margem.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }
}
