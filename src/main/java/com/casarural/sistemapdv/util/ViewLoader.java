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
import java.util.function.Consumer;

public class ViewLoader {

    public static <T> void showView(String fxmlPath, String title, Consumer<T> controllerAction) {
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


            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    if (Alerts.showConfirmation("Sair", null, "Deseja fechar esta tela?")) {
                        stage.close();
                    }
                    event.consume();
                }
            });

            stage.setScene(scene);
            stage.setResizable(false);
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.show();

        } catch (IOException e) {
            Alerts.showAlert("Erro Crítico", "Erro ao carregar a tela", e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        } catch (Exception e) {
            Alerts.showAlert("Erro Inesperado", "Ocorreu um erro ao abrir a janela", e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        }
    }

    public static void showView(String fxmlPath, String title) {
        showView(fxmlPath, title, null);
    }
}