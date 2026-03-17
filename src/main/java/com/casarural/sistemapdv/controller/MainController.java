package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.ViewLoader;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;


public class MainController {


    @FXML private Button botaoProdutos;
    @FXML private Button botaoClientes;

    @FXML
    public void onButtonProductAction() {
        ViewLoader.showView(
                "/com/casarural/sistemapdv/view/CadastroProduto.fxml",
                "Cadastro de Produto"
        );
    }


    @FXML
    public void onButtonCustomerAction() {
        ViewLoader.showView(
                "/com/casarural/sistemapdv/view/RegisterCustomer.fxml",
                "Cadastro de Cliente",
                (RegisterCustomerController controller) -> {

                }
        );
    }


    @FXML
    public void onBotaoPDVAction() {
        ViewLoader.showView("/com/casarural/sistemapdv/view/pdv.fxml", "Caixa Aberto");
    }
}

