package com.casarural.sistemapdv;

import com.casarural.sistemapdv.controller.MainController;
import com.casarural.sistemapdv.db.DB;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.sql.Connection;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/casarural/sistemapdv/view/main-view.fxml"));

        Connection conn = DB.getConnection();
        if (conn != null) {
            System.out.println("Banco de dados conectado com sucesso!");
        }

        MainController controller = new MainController();
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F2) {
                controller.onButtonProductAction();
                event.consume();
            }
            if (event.getCode() == KeyCode.F3) {
                controller.onButtonProductAction();
                event.consume();
            }
            if (event.getCode() == KeyCode.F1) {
                controller.onBotaoPDVAction();
                event.consume();
            }
        });

        stage.setTitle("Casa Rural");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
