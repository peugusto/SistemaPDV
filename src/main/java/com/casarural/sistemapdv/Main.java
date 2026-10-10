package com.casarural.sistemapdv;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.util.Alerts;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.sql.Connection;
import java.util.Objects;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/casarural/sistemapdv/view/main-view.fxml"));

        // Atualiza a estrutura do banco antes de qualquer tela. Se falhar, o sistema não abre:
        // usar um banco desatualizado ou atualizado pela metade pode gravar dados errados.
        try {
            DB.migrate();
        } catch (DbException e) {
            Alerts.showAlert("Banco de Dados", "O sistema não pode ser aberto", e.getMessage(), Alert.AlertType.ERROR);
            Platform.exit();
            return;
        }

        try (Connection conn = DB.getConnection()) {
            System.out.println("Banco de dados conectado com sucesso!");
        } catch (DbException e) {
            Alerts.showAlert("Banco de Dados", "Sem conexão com o banco", e.getMessage(), Alert.AlertType.WARNING);
        }

        // Os atalhos F1-F5 são registrados pelo próprio MainController (configurarAtalhos)
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/casarural/sistemapdv/css/style.css")).toExternalForm());

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
