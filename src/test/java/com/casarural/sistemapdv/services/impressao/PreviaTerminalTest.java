package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreviaTerminalTest {
    @Test
    void previaMostraMesmoTextoEAcentosSemAbrirAplicativoOuImprimir() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PreviaTerminal previa = new PreviaTerminal(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        ComprovanteVenda comprovante = ComprovanteVenda.gerar(ComprovanteVendaTest.venda(), Money.ZERO, Map.of());
        previa.enviar(comprovante);
        String saida = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(saida.contains(comprovante.getTexto()));
        assertTrue(saida.contains("Café"));
        assertTrue(saida.contains("nenhum trabalho enviado à impressora"));
    }

    @Test
    void falhaDeSaidaEhReportada() {
        OutputStream falha = new OutputStream() {
            @Override public void write(int valor) throws IOException { throw new IOException("Terminal indisponível"); }
        };
        PreviaTerminal previa = new PreviaTerminal(new PrintStream(falha));
        assertThrows(IOException.class, () -> previa.enviar(
                ComprovanteVenda.gerar(ComprovanteVendaTest.venda(), Money.ZERO, Map.of())));
    }
}
