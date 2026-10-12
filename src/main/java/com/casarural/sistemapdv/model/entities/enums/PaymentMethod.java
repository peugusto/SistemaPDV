package com.casarural.sistemapdv.model.entities.enums;

public enum PaymentMethod {
    DINHEIRO, CARTAO, FIADO, PIX;

    /**
     * Lê a forma gravada no banco. Valores antigos que não existem mais (ex.: "PAGO", dos primeiros
     * pedidos de teste) voltam null em vez de derrubar a tela.
     */
    public static PaymentMethod ler(String texto) {
        if (texto == null) return null;
        try {
            return valueOf(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
