package com.casarural.sistemapdv.services.impressao;

import java.io.IOException;
import java.io.PrintStream;

/** Prévia de desenvolvimento. Não procura impressoras nem abre aplicativos. */
public final class PreviaTerminal {
    private final PrintStream saida;

    public PreviaTerminal() { this(System.out); }

    public PreviaTerminal(PrintStream saida) { this.saida = saida; }

    public void enviar(Comprovante comprovante) throws IOException {
        saida.println("\n[PRÉVIA - nenhum trabalho enviado à impressora]\n" + comprovante.getTexto());
        if (saida.checkError()) throw new IOException("Não foi possível mostrar o comprovante no terminal.");
    }
}
