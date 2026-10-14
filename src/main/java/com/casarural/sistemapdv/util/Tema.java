package com.casarural.sistemapdv.util;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto único que aplica o visual do sistema em toda janela e aviso.
 *
 *   tema-claro.css / tema-alto-contraste.css -> só variáveis (cores e fonte)
 *   componentes.css                          -> desenho dos componentes, usando só as variáveis
 *   acessivel-foco.css                       -> (opcional) borda forte no campo selecionado
 *   tamanho da letra                         -> folha gerada ".root { -fx-font-size: Npx }", a última da lista
 *
 * Os componentes usam "em", então mudar a letra da .root aumenta tudo junto.
 * Toda janela nova deve chamar Tema.aplicar(scene). Avisos (Alerts) já chamam aplicar(dialogPane).
 * Ctrl + / Ctrl - / Ctrl 0 funcionam em qualquer janela que passou por aplicar(scene).
 */
public final class Tema {

    private static final Logger log = LoggerFactory.getLogger(Tema.class);
    private static final String PASTA = "/com/casarural/sistemapdv/css/";
    private static final String ATALHOS = "tema.atalhos";

    /** Temas de cor disponíveis (arquivo de variáveis). */
    public enum Cores {
        CLARO("Claro (padrão)", "tema-claro.css"),
        ALTO_CONTRASTE("Alto contraste", "tema-alto-contraste.css");

        private final String nome;
        private final String arquivo;

        Cores(String nome, String arquivo) {
            this.nome = nome;
            this.arquivo = arquivo;
        }

        public String getNome() { return nome; }
    }

    /** Tamanhos prontos do menu (px). O normal é o do tema-claro.css. */
    public static final int LETRA_PEQUENA = 12;
    public static final int LETRA_NORMAL = 14;
    public static final int LETRA_GRANDE = 17;
    public static final int LETRA_MUITO_GRANDE = 20;
    public static final int LETRA_MINIMA = 11;
    public static final int LETRA_MAXIMA = 24;

    private static Cores cores = Cores.CLARO;
    private static int tamanhoLetra = LETRA_NORMAL;
    private static boolean focoDestacado = false;
    /** Avisado quando algo muda (o menu marca a opção certa e salva a escolha). */
    private static final List<Runnable> aoMudar = new ArrayList<>();

    private Tema() {}

    // ===================== Aplicar =====================

    public static void aplicar(Scene scene) {
        scene.getStylesheets().setAll(folhas());
        instalarAtalhos(scene);
    }

    public static void aplicar(DialogPane dialogPane) {
        dialogPane.getStylesheets().setAll(folhas());
    }

    /** Reaplica em todas as janelas abertas (depois de mudar letra, cores ou foco). */
    public static void reaplicarTodas() {
        for (Window w : new ArrayList<>(Window.getWindows())) {
            Scene s = w.getScene();
            if (s == null) continue;
            if (s.getRoot() instanceof DialogPane) {
                aplicar((DialogPane) s.getRoot());
            } else {
                s.getStylesheets().setAll(folhas());
            }
            // a janela se ajusta ao novo tamanho de letra (as maximizadas continuam maximizadas)
            if (w instanceof javafx.stage.Stage && !((javafx.stage.Stage) w).isMaximized()) {
                w.sizeToScene();
            }
        }
    }

    // ===================== Preferências =====================

    public static Cores getCores() { return cores; }
    public static int getTamanhoLetra() { return tamanhoLetra; }
    public static boolean isFocoDestacado() { return focoDestacado; }

    /** Usado ao abrir o sistema, com o que estava salvo. Não avisa ninguém. */
    public static void configurar(Cores c, int letra, boolean foco) {
        cores = c == null ? Cores.CLARO : c;
        tamanhoLetra = limitar(letra);
        focoDestacado = foco;
    }

    public static void setCores(Cores c) {
        cores = c == null ? Cores.CLARO : c;
        mudou();
    }

    public static void setTamanhoLetra(int px) {
        tamanhoLetra = limitar(px);
        mudou();
    }

    /** +1 ou -1 passo (2 px). */
    public static void mudarLetra(int passos) {
        setTamanhoLetra(tamanhoLetra + passos * 2);
    }

    public static void setFocoDestacado(boolean foco) {
        focoDestacado = foco;
        mudou();
    }

    public static void aoMudar(Runnable r) {
        aoMudar.add(r);
    }

    static int limitar(int px) {
        return Math.max(LETRA_MINIMA, Math.min(LETRA_MAXIMA, px));
    }

    private static void mudou() {
        log.info("Visual: cores {}, letra {}px, foco destacado {}", cores, tamanhoLetra, focoDestacado);
        reaplicarTodas();
        for (Runnable r : aoMudar) r.run();
    }

    // ===================== Atalhos Ctrl + / Ctrl - / Ctrl 0 =====================

    private static void instalarAtalhos(Scene scene) {
        if (scene.getProperties().containsKey(ATALHOS)) return;
        scene.getProperties().put(ATALHOS, Boolean.TRUE);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isShortcutDown()) return;
            KeyCode k = e.getCode();
            if (k == KeyCode.PLUS || k == KeyCode.EQUALS || k == KeyCode.ADD) {
                mudarLetra(+1);
                e.consume();
            } else if (k == KeyCode.MINUS || k == KeyCode.SUBTRACT) {
                mudarLetra(-1);
                e.consume();
            } else if (k == KeyCode.DIGIT0 || k == KeyCode.NUMPAD0) {
                setTamanhoLetra(LETRA_NORMAL);
                e.consume();
            }
        });
    }

    // ===================== Estados de texto =====================

    private static final List<String> ESTADOS_TEXTO = List.of("texto-perigo", "texto-sucesso", "texto-alerta", "texto-suave");

    /**
     * Marca a cor de estado de um texto (ex.: célula de tabela) com as classes do componentes.css,
     * tirando a anterior. classe = "texto-perigo", "texto-sucesso", "texto-alerta" ou null (normal).
     * Células de tabela são reaproveitadas ao rolar, por isso é preciso tirar o estado anterior.
     */
    public static void estado(Node no, String classe) {
        no.getStyleClass().removeAll(ESTADOS_TEXTO);
        if (classe != null) {
            no.getStyleClass().add(classe);
        }
    }

    // ===================== Folhas =====================

    private static List<String> folhas() {
        List<String> lista = new ArrayList<>();
        List<String> arquivos = new ArrayList<>(List.of(cores.arquivo, "componentes.css"));
        if (focoDestacado) arquivos.add("acessivel-foco.css");
        for (String nome : arquivos) {
            URL url = Tema.class.getResource(PASTA + nome);
            if (url == null) {
                log.warn("Folha de estilo não encontrada: {}", PASTA + nome);
            } else {
                lista.add(url.toExternalForm());
            }
        }
        // Por último: ganha do -fx-font-size do arquivo de tema
        if (tamanhoLetra != LETRA_NORMAL) {
            String css = ".root { -fx-font-size: " + tamanhoLetra + "px; }";
            lista.add("data:text/css," + URLEncoder.encode(css, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return lista;
    }
}
