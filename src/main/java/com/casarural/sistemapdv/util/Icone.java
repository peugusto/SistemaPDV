package com.casarural.sistemapdv.util;

import javafx.collections.ListChangeListener;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ícone do sistema (a casinha verde) na barra de título e na barra de tarefas de todas as janelas.
 * O mesmo desenho está em instalador/icone.ico (atalho e instalador).
 */
public final class Icone {

    private static final Logger log = LoggerFactory.getLogger(Icone.class);
    private static final int[] TAMANHOS = {16, 32, 48, 64, 256};
    private static List<Image> imagens;

    private Icone() {
    }

    /** Chamar uma vez, na abertura: toda janela que aparecer depois (telas, avisos, diálogos) recebe o ícone. */
    public static void instalarEmTodasAsJanelas() {
        Window.getWindows().addListener((ListChangeListener<Window>) mudanca -> {
            while (mudanca.next()) {
                for (Window janela : mudanca.getAddedSubList()) {
                    if (janela instanceof Stage) aplicar((Stage) janela);
                }
            }
        });
    }

    public static void aplicar(Stage stage) {
        if (stage.getIcons().isEmpty()) stage.getIcons().setAll(imagens());
    }

    private static synchronized List<Image> imagens() {
        if (imagens == null) {
            imagens = new ArrayList<>();
            for (int t : TAMANHOS) {
                String caminho = "/com/casarural/sistemapdv/icone/icone-" + t + ".png";
                try (InputStream in = Icone.class.getResourceAsStream(caminho)) {
                    if (in != null) imagens.add(new Image(in));
                } catch (Exception e) {
                    // sem ícone a janela abre do mesmo jeito
                    log.warn("Ícone {} não carregado", caminho, e);
                }
            }
        }
        return imagens;
    }
}
