package com.casarural.sistemapdv.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class RegisterCustomerController {
    @FXML
    private Button btnCancel;

    @FXML
    public void onBtnCancelAction() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}
