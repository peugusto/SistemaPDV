package com.casarural.sistemapdv.services.impressao;

import javax.print.PrintService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Arrays;

/** Usa o driver instalado no Windows, preservando acentos sem depender de ESC/POS/code page. */
public final class ImpressoraTermica {
    private final String nome;

    public ImpressoraTermica(String nome) { this.nome = nome; }

    public void enviar(ComprovanteVenda comprovante) throws PrinterException {
        PrintService impressora = Arrays.stream(PrinterJob.lookupPrintServices())
                .filter(p -> p.getName().equalsIgnoreCase(nome)).findFirst()
                .orElseThrow(() -> new PrinterException("Impressora '" + nome + "' não encontrada no Windows."));
        PrinterJob trabalho = PrinterJob.getPrinterJob();
        trabalho.setPrintService(impressora);
        trabalho.setCopies(1);
        trabalho.setJobName("Casa Rural - Venda #" + comprovante.getIdVenda());
        String[] linhas = comprovante.getTexto().split("\n");
        Paper papel = new Paper();
        double largura = mm(80);
        double altura = Math.max(mm(100), Math.min(mm(297), linhas.length * 11.0 + mm(12)));
        papel.setSize(largura, altura);
        papel.setImageableArea(mm(4), mm(4), largura - mm(8), altura - mm(8));
        PageFormat formato = new PageFormat();
        formato.setPaper(papel);
        formato = trabalho.validatePage(formato);
        if (formato.getWidth() > mm(85) || formato.getImageableWidth() < mm(60)) {
            throw new PrinterException("Configure a bobina de 80 mm nas preferências da impressora '" + nome + "'.");
        }
        trabalho.setPrintable(new TextoTermico(linhas), formato);
        // A confirmação é de envio ao spooler, não de saída física do papel. Não repetir automaticamente.
        trabalho.print();
    }

    private static double mm(double valor) { return valor * 72 / 25.4; }

    /** Pagina também vendas longas: nenhuma linha é descartada no fim da página do driver. */
    public static final class TextoTermico implements Printable {
        private final String[] linhas;

        public TextoTermico(String[] linhas) { this.linhas = linhas.clone(); }

        @Override
        public int print(Graphics graphics, PageFormat formato, int pagina) throws PrinterException {
            if (pagina < 0 || formato.getImageableWidth() <= 0 || formato.getImageableHeight() < 12) {
                throw new PrinterException("Área de impressão inválida.");
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                Font fonte = new Font(Font.MONOSPACED, Font.PLAIN, 8);
                double larguraTexto = g.getFontMetrics(fonte).charWidth('M') * ComprovanteVenda.COLUNAS;
                if (larguraTexto > formato.getImageableWidth()) {
                    fonte = fonte.deriveFont((float) (8 * formato.getImageableWidth() / larguraTexto));
                }
                g.setFont(fonte);
                int passo = g.getFontMetrics().getHeight() + 1;
                int porPagina = Math.max(1, (int) formato.getImageableHeight() / passo);
                long inicio = (long) pagina * porPagina;
                if (inicio >= linhas.length) return NO_SUCH_PAGE;
                g.translate(formato.getImageableX(), formato.getImageableY());
                g.setColor(Color.BLACK);
                int y = g.getFontMetrics().getAscent();
                for (int i = (int) inicio; i < Math.min(linhas.length, inicio + porPagina); i++) {
                    g.drawString(linhas[i], 0, y);
                    y += passo;
                }
                return PAGE_EXISTS;
            } finally {
                g.dispose();
            }
        }
    }
}
