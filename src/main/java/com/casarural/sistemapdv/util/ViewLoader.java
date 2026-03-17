package com.casarural.sistemapdv.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

            // Pega o controller da janela que acabou de ser carregada
            T controller = loader.getController();

            // Se você passou alguma instrução (Lambda), ela é executada aqui
            if (controllerAction != null && controller != null) {
                controllerAction.accept(controller);
            }

            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));

            // Configurações padrão para janelas de PDV
            stage.setResizable(false);
            stage.initModality(Modality.APPLICATION_MODAL); // Trava a tela de fundo

            stage.show();

        } catch (IOException e) {
            // Usa o seu utilitário de alertas que já criamos
            Alerts.showAlert("Erro Crítico", "Erro ao carregar a tela", e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        } catch (Exception e) {
            Alerts.showAlert("Erro Inesperado", "Ocorreu um erro ao abrir a janela", e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        }
    }

    // Sobrecarga simples: caso você só queira abrir a janela sem configurar nada nela
    public static void showView(String fxmlPath, String title) {
        showView(fxmlPath, title, null);
    }
}