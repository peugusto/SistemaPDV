package com.casarural.sistemapdv.model.entities.enums;

/** De onde veio um pagamento (coluna pagamento.origem, migração V3). */
public enum PaymentOrigin {
    /** Pago na hora da compra, na tela de pagamento do PDV. */
    VENDA,
    /** Pago depois, abatendo dívida de fiado. */
    RECEBIMENTO_FIADO
}
