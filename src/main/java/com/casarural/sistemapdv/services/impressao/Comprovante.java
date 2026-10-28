package com.casarural.sistemapdv.services.impressao;

/** Documento já formatado: o destino de impressão não consulta nem altera dados financeiros. */
public interface Comprovante {
    String getTexto();
    String getNomeTrabalho();
}
