package com.casarural.sistemapdv.model.entities.enums;

public enum OrderStatus {
    PAGO, FIADO, EM_ANDAMENTO,
    /** Venda desfeita (cancelada ou trocada). Os pagamentos dela foram para pagamento_estornado (V13). */
    CANCELADO;
}
