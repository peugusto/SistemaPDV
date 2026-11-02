package com.casarural.sistemapdv.services.impressao;

import java.io.IOException;

/** Encerrar o processo não desfaz um trabalho que o spooler já tenha aceitado. */
public final class EnvioImpressaoIncertoException extends IOException {
    public EnvioImpressaoIncertoException() {
        super("O envio à impressora não pôde ser confirmado e pode já ter sido aceito. Confira a fila da impressora e o papel antes de solicitar uma segunda via. Não refaça a venda nem o pagamento.");
    }
}
