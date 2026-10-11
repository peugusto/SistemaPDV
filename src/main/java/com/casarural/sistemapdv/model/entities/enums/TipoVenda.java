package com.casarural.sistemapdv.model.entities.enums;

/** Como o produto é vendido. Coluna produto.tipo_venda (V11). */
public enum TipoVenda {
    /** Por unidade, com preço fixo (como sempre foi). */
    UNIDADE,
    /** Pesado na balança: o caixa digita o valor que a balança mostra. Nunca controla estoque. */
    PESAVEL;
}
