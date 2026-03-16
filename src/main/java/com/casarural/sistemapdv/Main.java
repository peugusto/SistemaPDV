package com.casarural.sistemapdv;

import com.casarural.sistemapdv.db.DB;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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

        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

        stage.setTitle("Sistema PDV - Casa Rural");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
