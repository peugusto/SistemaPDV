package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class CustomerFormController implements Initializable {

    private Customer entity;
    private CustomerService service;
    private CustomerListController listController;

    @FXML private TextField txtNome;
    @FXML private TextField txtLimite;
    @FXML private ComboBox<CustomerStatus> comboBoxStatus;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    public void setCustomer(Customer entity) {
        this.entity = entity;
    }

    public void setServices(CustomerService service, CustomerListController listController) {
        this.service = service;
        this.listController = listController;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeComboBox();
    }

    private void initializeComboBox() {
        comboBoxStatus.setItems(FXCollections.observableArrayList(CustomerStatus.values()));
    }


    public void updateFormData() {
        if (entity == null) {
            throw new IllegalStateException("Entity foi enviada nula");
        }
        txtNome.setText(entity.getNomeCliente());
        txtLimite.setText(String.valueOf(entity.getLimiteCredito()));
        comboBoxStatus.setValue(entity.getSituacaoFiado());
    }

    @FXML
    public void onBtnSaveAction(ActionEvent event) {
        if (entity == null || service == null) {
            throw new IllegalStateException("Service ou Entity nulos");
        }

        try {

            entity.setNomeCliente(txtNome.getText());
            entity.setLimiteCredito(Double.parseDouble(txtLimite.getText()));
            entity.setSituacaoFiado(comboBoxStatus.getValue());


            service.saveOrUpdate(entity);


            listController.updateTableView();


            closeStage(event);

            Alerts.showAlert("Sucesso", null, "Cliente atualizado com sucesso!", Alert.AlertType.INFORMATION);

        } catch (NumberFormatException e) {
            Alerts.showAlert("Erro de Validação", null, "Por favor, digite um valor numérico válido para o limite.", Alert.AlertType.ERROR);
        } catch (DbException e) {

            Alerts.showAlert("Erro de Negócio", "Não foi possível salvar", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void onBtnCancelAction(ActionEvent event) {
        closeStage(event);
    }

    private void closeStage(ActionEvent event) {
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }
}