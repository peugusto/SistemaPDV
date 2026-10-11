package com.casarural.sistemapdv.model.entities.enums;

/** Dinheiro que entra ou sai da gaveta sem ser venda (tabela movimento_caixa). */
public enum CashMovementType {
    /** Retirada: pagar fornecedor, levar dinheiro para casa. */
    SANGRIA,
    /** Entrada: trazer mais troco. */
    REFORCO
}
