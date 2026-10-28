package com.casarural.sistemapdv.services.impressao;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.PrintQuality;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Usa o driver instalado no Windows, preservando acentos sem depender de ESC/POS/code page. */
public final class ImpressoraTermica {
    private static final double LARGURA_MM = 70;
    private static final double ALTURA_MM = 40;
    private static final double MARGEM_MM = 2;
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
        PageFormat formato = trabalho.validatePage(formatoEtiqueta());
        validarFormato(formato);
        trabalho.setPrintable(new TextoTermico(linhas), formato);
        // A confirmação é de envio ao spooler, não de saída física do papel. Não repetir automaticamente.
        trabalho.print(qualidade(impressora));
    }

    static PageFormat formatoEtiqueta() {
        Paper papel = new Paper();
        double largura = mm(LARGURA_MM);
        double altura = mm(ALTURA_MM);
        papel.setSize(largura, altura);
        papel.setImageableArea(mm(MARGEM_MM), mm(MARGEM_MM),
                largura - mm(MARGEM_MM * 2), altura - mm(MARGEM_MM * 2));
        PageFormat formato = new PageFormat();
        formato.setPaper(papel);
        return formato;
    }

    static void validarFormato(PageFormat formato) throws PrinterException {
        if (Math.abs(formato.getWidth() - mm(LARGURA_MM)) > mm(0.5)
                || Math.abs(formato.getHeight() - mm(ALTURA_MM)) > mm(0.5)) {
            throw new PrinterException("O driver alterou o tamanho da etiqueta. Configure 70 mm de largura "
                    + "por 40 mm de altura, sem rotação, nas preferências da impressora.");
        }
    }

    static PrintRequestAttributeSet qualidade(PrintService impressora) {
        PrintRequestAttributeSet atributos = new HashPrintRequestAttributeSet();
        if (impressora.isAttributeValueSupported(PrintQuality.HIGH, null, null)) atributos.add(PrintQuality.HIGH);
        return atributos;
    }

    private static double mm(double valor) { return valor * 72 / 25.4; }

    /** Mantém a leitura: o que não cabe nos 40 mm continua na próxima etiqueta. */
    public static final class TextoTermico implements Printable {
        private final String[] linhas;

        public TextoTermico(String[] linhas) { this.linhas = linhas.clone(); }

        @Override
        public int print(Graphics graphics, PageFormat formato, int pagina) throws PrinterException {
            // Interseção entre as margens do comprovante e a área física permitida pelo driver.
            double esquerda = Math.max(mm(MARGEM_MM), formato.getImageableX());
            double topo = Math.max(mm(MARGEM_MM), formato.getImageableY());
            double direita = Math.min(formato.getWidth() - mm(MARGEM_MM),
                    formato.getImageableX() + formato.getImageableWidth());
            double base = Math.min(formato.getHeight() - mm(MARGEM_MM),
                    formato.getImageableY() + formato.getImageableHeight());
            double largura = direita - esquerda;
            double altura = base - topo;
            if (pagina < 0 || largura <= 0 || altura < 12) {
                throw new PrinterException("Área de impressão inválida.");
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                // Letras sólidas: evita pixels cinza que deixam o texto térmico mais fraco.
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                Font fonte = new Font("Consolas", Font.BOLD, 9);
                if (!fonte.getFamily().equalsIgnoreCase("Consolas")) fonte = new Font(Font.MONOSPACED, Font.BOLD, 9);
                double larguraCelula = fonte.getStringBounds("M", g.getFontRenderContext()).getWidth();
                double larguraTexto = larguraCelula * ComprovanteVenda.COLUNAS;
                // Mede novamente em cada tamanho: o arredondamento em 203 dpi pode mudar a largura.
                for (float tamanho = 8.75f; larguraTexto > largura && tamanho >= 7; tamanho -= 0.25f) {
                    fonte = fonte.deriveFont(tamanho);
                    larguraCelula = fonte.getStringBounds("M", g.getFontRenderContext()).getWidth();
                    larguraTexto = larguraCelula * ComprovanteVenda.COLUNAS;
                }
                g.setFont(fonte);
                FontMetrics metricas = g.getFontMetrics();
                int passo = metricas.getHeight();
                int porPagina = (int) (altura / passo);
                if (porPagina < 1 || larguraTexto > largura + 0.1) {
                    throw new PrinterException("A área do driver é pequena demais para o comprovante de 70 x 40 mm.");
                }
                List<List<String>> paginas = paginar(linhas, porPagina);
                if (pagina >= paginas.size()) return NO_SUCH_PAGE;
                g.clip(new Rectangle2D.Double(esquerda, topo, largura, altura));
                g.translate(esquerda + Math.max(0, (largura - larguraTexto) / 2), topo);
                g.setColor(Color.BLACK);
                int y = metricas.getAscent();
                for (String linha : paginas.get(pagina)) {
                    // Grade de colunas fixa: o driver não pode alterar o espaçamento de uma linha monetária.
                    int coluna = 0;
                    for (int posicao = 0; posicao < linha.length();) {
                        int caractere = linha.codePointAt(posicao);
                        g.drawString(new String(Character.toChars(caractere)), (float) (coluna++ * larguraCelula), y);
                        posicao += Character.charCount(caractere);
                    }
                    y += passo;
                }
                return PAGE_EXISTS;
            } finally {
                g.dispose();
            }
        }
    }

    /** Evita quebrar um item entre seu nome (inclusive nomes longos) e a linha de quantidade/valor. */
    static List<List<String>> paginar(String[] linhas, int capacidade) {
        if (capacidade < 1) throw new IllegalArgumentException("Capacidade da etiqueta inválida.");
        List<List<String>> paginas = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        for (int i = 0; i < linhas.length;) {
            int fim = i + 1;
            if (linhas[i].matches("\\d{2,} .+")) {
                while (fim < linhas.length && !linhas[fim].matches(" {3}\\d+ x .+")
                        && !linhas[fim].matches("\\d{2,} .+") && !linhas[fim].startsWith("---")) fim++;
                fim = fim < linhas.length && linhas[fim].matches(" {3}\\d+ x .+") ? fim + 1 : i + 1;
            }
            int tamanho = fim - i;
            if (!atual.isEmpty() && tamanho <= capacidade && atual.size() + tamanho > capacidade) {
                paginas.add(atual);
                atual = new ArrayList<>();
            }
            while (i < fim) {
                if (atual.size() == capacidade) {
                    paginas.add(atual);
                    atual = new ArrayList<>();
                }
                atual.add(linhas[i++]);
            }
        }
        if (!atual.isEmpty()) paginas.add(atual);
        return paginas;
    }
}
