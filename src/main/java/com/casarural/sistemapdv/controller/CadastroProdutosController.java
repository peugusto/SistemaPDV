package com.casarural.sistemapdv.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class CadastroProdutosController {

    @FXML
    private Button botaoCancelar;

    @FXML
    public void onBotaoCancelarAction() {
        Stage stage = (Stage) botaoCancelar.getScene().getWindow();
        stage.close();
    }

}
