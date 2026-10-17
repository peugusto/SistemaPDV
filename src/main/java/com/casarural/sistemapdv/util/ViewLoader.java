package com.casarural.sistemapdv.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.Alert.AlertType;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

public class ViewLoader {

    private static final Logger log = LoggerFactory.getLogger(ViewLoader.class);

    public static <T> void showView(Boolean isFullScreen, String fxmlPath, String title, Consumer<T> controllerAction) {
        try {
            FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(fxmlPath));
            Parent root = loader.load();

            T controller = loader.getController();

            if (controllerAction != null && controller != null) {
                controllerAction.accept(controller);
            }

            Stage stage = new Stage();
            stage.setTitle(title);

            Scene scene = new Scene(root);

            Tema.aplicar(scene);


            // Handler (e não filtro): roda depois da tela. Se a tela já usou o Esc (PDV cancelando a venda,
            // combo fechando a lista), o evento chega consumido e não pergunta se é para fechar.
            scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE && !event.isConsumed()) {
                    if (Alerts.showConfirmation("Sair", null, "Deseja fechar esta tela?")) {
                        stage.close();
                    }
                    event.consume();
                }
            });

            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);

            if (isFullScreen) {
                stage.setResizable(true);
                stage.setMaximized(true);
            } else {
                stage.setResizable(false);
            }

            stage.showAndWait();

        } catch (IOException e) {
            log.error("Erro ao carregar a tela {}", fxmlPath, e);
            Alerts.showAlert("Erro Crítico", "Erro ao carregar a tela", e.getMessage(), AlertType.ERROR);
        } catch (Exception e) {
            log.error("Erro inesperado na tela {}", fxmlPath, e);
            Alerts.showAlert("Erro Inesperado", "Ocorreu um erro ao abrir a janela", e.getMessage(), AlertType.ERROR);
        }
    }

    public static void showView( Boolean isFullScreen, String fxmlPath, String title) {
        showView(isFullScreen,fxmlPath, title, null);
    }
}