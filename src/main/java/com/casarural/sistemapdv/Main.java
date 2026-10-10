package com.casarural.sistemapdv;

import com.casarural.sistemapdv.controller.MainController;
import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.util.Alerts;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.sql.Connection;
import java.util.Objects;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/casarural/sistemapdv/view/main-view.fxml"));

        // Testa a conexão ao abrir; se o MySQL estiver fora, avisa mas abre o sistema
        // (o pool tenta conectar de novo na próxima operação)
        try (Connection conn = DB.getConnection()) {
            System.out.println("Banco de dados conectado com sucesso!");
        } catch (DbException e) {
            Alerts.showAlert("Banco de Dados", "Sem conexão com o banco", e.getMessage(), Alert.AlertType.WARNING);
        }

        MainController controller = new MainController();
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/casarural/sistemapdv/css/style.css")).toExternalForm());
        stage.setScene(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case F1 -> controller.onBotaoPDVAction();
                case F2 -> controller.onButtonProductAction();
                case F3 -> controller.onButtonProductListAction();
                case F4 -> controller.onButtonCustomerAction();
                case F5 -> controller.onButtonCustomerListAction();
                case ESCAPE -> {
                }
                default -> { return; }
            }
            event.consume();
        });

        stage.setTitle("Casa Rural");
        stage.setMaximized(true);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        DB.closeDataSource();
    }

    public static void main(String[] args) {
        launch();
    }
}
