package com.casarural.sistemapdv.model.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** A chave identifica uma entrada, inclusive quando sua confirmação precisa ser repetida. */
public final class EntradaEstoque {
    private static final BigDecimal CUSTO_MAXIMO = new BigDecimal("99999999.99");
    private final UUID idOperacao;
    private final int idProduto;
    private final int quantidade;
    /** null mantém o custo atual do banco. */
    private final BigDecimal custoInformado;

    public EntradaEstoque(UUID idOperacao, int idProduto, int quantidade, BigDecimal custoInformado) {
        if (idOperacao == null) throw new IllegalArgumentException("Identificação da entrada não informada.");
        if (idProduto <= 0) throw new IllegalArgumentException("Pesquise o produto antes de dar entrada.");
        if (quantidade <= 0) throw new IllegalArgumentException("Digite uma quantidade de entrada maior que zero.");
        this.idOperacao = idOperacao;
        this.idProduto = idProduto;
        this.quantidade = quantidade;
        this.custoInformado = validarCusto(custoInformado);
    }

    public static BigDecimal validarCusto(BigDecimal custoInformado) {
        if (custoInformado != null) {
            try {
                custoInformado = custoInformado.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("O custo deve ter no máximo duas casas decimais.");
            }
            if (custoInformado.signum() < 0 || custoInformado.compareTo(CUSTO_MAXIMO) > 0) {
                throw new IllegalArgumentException("O custo deve estar entre R$ 0,00 e R$ 99.999.999,99.");
            }
        }
        return custoInformado;
    }

    public UUID getIdOperacao() { return idOperacao; }
    public int getIdProduto() { return idProduto; }
    public int getQuantidade() { return quantidade; }
    public BigDecimal getCustoInformado() { return custoInformado; }

    public boolean mesmosDados(int produto, int quantidade, BigDecimal custo) {
        return idProduto == produto && this.quantidade == quantidade
                && Objects.equals(custoInformado, custo == null ? null : custo.setScale(2));
    }
}
