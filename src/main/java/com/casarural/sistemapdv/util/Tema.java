package com.casarural.sistemapdv.util;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto único que aplica o visual do sistema em toda janela e aviso.
 *
 *   tema-claro.css   -> só variáveis (cores e fonte). Futuro: outros temas trocam este arquivo.
 *   componentes.css  -> desenho dos componentes, usando só as variáveis.
 *
 * Toda janela nova deve chamar Tema.aplicar(scene). Avisos (Alerts) já chamam aplicar(dialogPane).
 */
public final class Tema {

    private static final Logger log = LoggerFactory.getLogger(Tema.class);
    private static final String PASTA = "/com/casarural/sistemapdv/css/";

    /** Arquivo de cores em uso. Quando existirem temas, a escolha do usuário muda este valor. */
    private static String arquivoTema = "tema-claro.css";

    private Tema() {}

    public static void aplicar(Scene scene) {
        scene.getStylesheets().setAll(folhas());
    }

    public static void aplicar(DialogPane dialogPane) {
        dialogPane.getStylesheets().setAll(folhas());
    }

    private static final List<String> ESTADOS_TEXTO = List.of("texto-perigo", "texto-sucesso", "texto-alerta");

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

    private static List<String> folhas() {
        List<String> lista = new ArrayList<>();
        for (String nome : new String[]{arquivoTema, "componentes.css"}) {
            URL url = Tema.class.getResource(PASTA + nome);
            if (url == null) {
                log.warn("Folha de estilo não encontrada: {}", PASTA + nome);
            } else {
                lista.add(url.toExternalForm());
            }
        }
        return lista;
    }
}
