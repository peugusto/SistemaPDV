package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.util.Alerts;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class RegisterCustomerController {
    @FXML
    private Button btnCancel;

    @FXML
    public void onBtnCancelAction() {
        if (Alerts.showConfirmation("Sair", "Deseja voltar ao menu?", "Dados não salvos serão perdidos.")) {
            Stage stage = (Stage) btnCancel.getScene().getWindow();
            stage.close();
        }
    }
}
