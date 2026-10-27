package com.casarural.sistemapdv.services.impressao;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ImpressoraTermicaTest {
    @Test
    void vendaLongaContinuaEmNovasPaginasSemEnviarTrabalhoReal() throws Exception {
        String[] linhas = IntStream.range(0, 100).mapToObj(i -> "Produto " + i).toArray(String[]::new);
        ImpressoraTermica.TextoTermico texto = new ImpressoraTermica.TextoTermico(linhas);
        Paper papel = new Paper();
        papel.setSize(227, 100);
        papel.setImageableArea(10, 10, 207, 80);
        PageFormat formato = new PageFormat();
        formato.setPaper(papel);
        Graphics2D g = new BufferedImage(227, 100, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            int paginas = 0;
            while (texto.print(g, formato, paginas) == Printable.PAGE_EXISTS) {
                assertTrue(++paginas < 100);
            }
            assertTrue(paginas > 1);
            assertEquals(Printable.PAGE_EXISTS, texto.print(g, formato, paginas - 1));
            assertEquals(Printable.NO_SUCH_PAGE, texto.print(g, formato, paginas));
        } finally {
            g.dispose();
        }
    }

    @Test
    void areaSemAlturaEhRecusada() {
        Paper papel = new Paper();
        papel.setImageableArea(0, 0, 200, 0);
        PageFormat formato = new PageFormat();
        formato.setPaper(papel);
        Graphics2D g = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            assertThrows(PrinterException.class, () -> new ImpressoraTermica.TextoTermico(new String[]{"teste"})
                    .print(g, formato, 0));
        } finally {
            g.dispose();
        }
    }
}
