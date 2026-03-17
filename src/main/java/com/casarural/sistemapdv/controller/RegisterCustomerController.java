package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterCustomerController implements Initializable {

    private CustomerService service;
    private Customer entity;

    @FXML private TextField txtNome;
    @FXML private TextField txtPhoneNumber;
    @FXML private TextField txtLimite;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    public void setCustomerService(CustomerService service) {
        this.service = service;
    }

    public void setCustomer(Customer entity) {
        this.entity = entity;
    }

    @FXML
    public void onBtnSaveAction() {
        if (service == null) {
            throw new IllegalStateException("Service estava nulo");
        }
        try {
            Customer obj = getFormData();
            service.insert(obj);
            Alerts.showAlert("Sucesso", null, "Cliente cadastrado com sucesso!", AlertType.INFORMATION);
            closeStage();
        } catch (RuntimeException e) {
            Alerts.showAlert("Erro de Validação", null, e.getMessage(), AlertType.ERROR);
        } catch (Exception e) {
            Alerts.showAlert("Erro no Banco", "Não foi possível salvar", e.getMessage(), AlertType.ERROR);
        }
    }

    private Customer getFormData() {
        if (txtNome.getText().trim().isEmpty()) {
            throw new RuntimeException("O nome do cliente é obrigatório.");
        }

        if (entity == null) {
            entity = new Customer();
        }

        entity.setNomeCliente(txtNome.getText());

        // Se o limite estiver vazio, assume 0.0
        String limiteStr = txtLimite.getText().replace(",", ".");
        entity.setLimiteCredito(limiteStr.isEmpty() ? 0.0 : Double.parseDouble(limiteStr));

        return entity;
    }

    @FXML
    public void onBtnCancelAction() {
        if (Alerts.showConfirmation("Sair", "Deseja voltar ao menu?", "Dados não salvos serão perdidos.")) {
            closeStage();
        }
    }

    private void closeStage() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Constraints.setTextFieldMaxLength(txtNome, 30);
        Constraints.setTextFieldDouble(txtLimite);

    }
}