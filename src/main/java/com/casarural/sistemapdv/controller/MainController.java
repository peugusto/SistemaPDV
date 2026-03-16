package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.util.Alerts;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;

public class MainController {

    @FXML
    private Button botaoProdutos;

    @FXML
    public void onBotaoProdutosAction() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/CadastroProduto.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Cadastro de Produto");

            stage.setScene(new Scene(root));

            stage.setResizable(false);
            stage.initModality(Modality.APPLICATION_MODAL);

            stage.show();

        } catch (IOException e) {
            Alerts.showAlert("Erro", null, e.getMessage(), AlertType.ERROR);
            e.printStackTrace();
        }
    }
}
