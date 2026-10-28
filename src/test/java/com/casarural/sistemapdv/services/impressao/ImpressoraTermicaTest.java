package com.casarural.sistemapdv.services.impressao;

import org.junit.jupiter.api.Test;

import javax.print.PrintService;
import javax.print.attribute.standard.PrintQuality;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ImpressoraTermicaTest {
    @Test
    void nomeDoProdutoEQuantidadeContinuamJuntosNaProximaEtiqueta() {
        String[] linhas = {"CASA RURAL", "Venda: #123", "01 Arroz especial", "nome que continuou em outra linha",
                "   2 x R$ 12,50              R$ 25,00", "TOTAL R$ 25,00"};
        List<List<String>> paginas = ImpressoraTermica.paginar(linhas, 4);
        assertEquals(List.of("CASA RURAL", "Venda: #123"), paginas.get(0));
        assertEquals(List.of(linhas[2], linhas[3], linhas[4], linhas[5]), paginas.get(1));
        assertEquals(List.of(linhas), paginas.stream().flatMap(List::stream).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void nomeMaiorQueUmaEtiquetaContinuaSemDescartarLinhas() {
        String[] linhas = {"01 Produto", "nome muito longo", "continuação do nome", "última parte do nome",
                "   1 x R$ 5,00               R$ 5,00", "TOTAL R$ 5,00"};
        List<List<String>> paginas = ImpressoraTermica.paginar(linhas, 3);
        assertEquals(2, paginas.size());
        assertEquals(List.of(linhas), paginas.stream().flatMap(List::stream).collect(java.util.stream.Collectors.toList()));
        paginas.forEach(pagina -> assertTrue(pagina.size() <= 3));
    }

    @Test
    void etiquetaTem70Por40MmEMargensDe2Mm() throws Exception {
        PageFormat formato = ImpressoraTermica.formatoEtiqueta();
        assertEquals(70, formato.getWidth() * 25.4 / 72, 0.001);
        assertEquals(40, formato.getHeight() * 25.4 / 72, 0.001);
        assertEquals(2, formato.getImageableX() * 25.4 / 72, 0.001);
        assertEquals(2, formato.getImageableY() * 25.4 / 72, 0.001);
        assertEquals(66, formato.getImageableWidth() * 25.4 / 72, 0.001);
        assertEquals(36, formato.getImageableHeight() * 25.4 / 72, 0.001);
        ImpressoraTermica.validarFormato(formato);
    }

    @Test
    void driverNaoPodeSubstituirEtiquetaPorBobinaOuGirarOPapel() {
        for (double[] tamanho : new double[][]{{80, 100}, {40, 70}, {210, 297}}) {
            Paper papel = new Paper();
            papel.setSize(tamanho[0] * 72 / 25.4, tamanho[1] * 72 / 25.4);
            PageFormat formato = new PageFormat();
            formato.setPaper(papel);
            PrinterException erro = assertThrows(PrinterException.class, () -> ImpressoraTermica.validarFormato(formato));
            assertTrue(erro.getMessage().contains("70 mm"));
        }
    }

    @Test
    void textoSaiPretoSolidoCentralizadoESemInvadirAsMargensEm203Dpi() throws Exception {
        BufferedImage imagem = etiquetaBranca();
        Graphics2D g = imagem.createGraphics();
        try {
            g.scale(203.0 / 72, 203.0 / 72);
            assertEquals(Printable.PAGE_EXISTS, new ImpressoraTermica.TextoTermico(
                    new String[]{"M".repeat(ComprovanteVenda.COLUNAS)}).print(g, ImpressoraTermica.formatoEtiqueta(), 0));
        } finally {
            g.dispose();
        }
        int menorX = imagem.getWidth(), maiorX = -1;
        for (int y = 0; y < imagem.getHeight(); y++) {
            for (int x = 0; x < imagem.getWidth(); x++) {
                int cor = imagem.getRGB(x, y) & 0xffffff;
                if (cor == 0xffffff) continue;
                assertEquals(0, cor, "Texto térmico não deve conter cinza de suavização.");
                assertTrue(x >= 2 * 203 / 25.4 && x < 68 * 203 / 25.4);
                assertTrue(y >= 2 * 203 / 25.4 && y < 38 * 203 / 25.4);
                menorX = Math.min(menorX, x);
                maiorX = Math.max(maiorX, x);
            }
        }
        assertTrue(maiorX > menorX, "O comprovante precisa ter sido desenhado.");
        assertEquals(imagem.getWidth() / 2.0, (menorX + maiorX) / 2.0, 3,
                "O bloco deve ficar no centro da etiqueta, sem deslocamento lateral.");
    }

    @Test
    void negritoTemMaisPretoQueAFonteAnterior() throws Exception {
        String linha = "M".repeat(ComprovanteVenda.COLUNAS);
        BufferedImage nova = etiquetaBranca();
        Graphics2D atual = nova.createGraphics();
        try {
            atual.scale(203.0 / 72, 203.0 / 72);
            new ImpressoraTermica.TextoTermico(new String[]{linha}).print(atual, ImpressoraTermica.formatoEtiqueta(), 0);
        } finally { atual.dispose(); }
        BufferedImage anterior = etiquetaBranca();
        Graphics2D antiga = anterior.createGraphics();
        try {
            antiga.scale(203.0 / 72, 203.0 / 72);
            antiga.setColor(Color.BLACK);
            antiga.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
            antiga.drawString(linha, 6, 6 + antiga.getFontMetrics().getAscent());
        } finally { antiga.dispose(); }
        assertTrue(pixelsPretos(nova) > pixelsPretos(anterior), "O novo texto deve ter traços mais fortes.");
    }

    @Test
    void solicitaQualidadeAltaApenasQuandoODriverSuporta() {
        for (boolean suporta : new boolean[]{false, true}) {
            PrintService impressora = (PrintService) Proxy.newProxyInstance(PrintService.class.getClassLoader(),
                    new Class<?>[]{PrintService.class}, (proxy, metodo, argumentos) -> {
                        if (metodo.getName().equals("isAttributeValueSupported")) return suporta;
                        throw new AssertionError("Consulta inesperada: " + metodo.getName());
                    });
            assertEquals(suporta ? PrintQuality.HIGH : null,
                    ImpressoraTermica.qualidade(impressora).get(PrintQuality.class));
        }
    }

    private static BufferedImage etiquetaBranca() {
        BufferedImage imagem = new BufferedImage((int) Math.ceil(70 * 203 / 25.4),
                (int) Math.ceil(40 * 203 / 25.4), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imagem.getWidth(), imagem.getHeight());
        g.dispose();
        return imagem;
    }

    private static long pixelsPretos(BufferedImage imagem) {
        long total = 0;
        for (int y = 0; y < imagem.getHeight(); y++) {
            for (int x = 0; x < imagem.getWidth(); x++) if ((imagem.getRGB(x, y) & 0xffffff) == 0) total++;
        }
        return total;
    }

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
