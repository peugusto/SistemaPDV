package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class CustomerListController implements Initializable {

    private CustomerService service;

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> creditStatusColumn;
    @FXML private TableColumn<Customer, Double> creditLimitColumn;
    @FXML private TableColumn<Customer, Void> actionsColumn;

    private ObservableList<Customer> obsList;

    public void setCustomerService(CustomerService service) {
        this.service = service;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {
        // Liga as colunas aos atributos da classe Customer
        idColumn.setCellValueFactory(new PropertyValueFactory<>("idCliente"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("nomeCliente"));
        creditStatusColumn.setCellValueFactory(new PropertyValueFactory<>("situacaoFiado"));
        creditLimitColumn.setCellValueFactory(new PropertyValueFactory<>("limiteCredito"));

        // Faz a tabela acompanhar a altura da janela
//        Stage stage = (Stage) customerTable.getScene().getWindow();
//        customerTable.prefHeightProperty().bind(stage.heightProperty());

        initButtons();
    }

    public void updateTableView() {
        if (service == null) {
            throw new IllegalStateException("Service was null");
        }
        List<Customer> list = service.findAll();
        obsList = FXCollections.observableArrayList(list);
        customerTable.setItems(obsList);
    }

    private void initButtons() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                btnEdit.setOnAction(event -> {
                    Customer obj = getTableView().getItems().get(getIndex());
                    onEditAction(obj);
                });

                btnDelete.setOnAction(event -> {
                    Customer obj = getTableView().getItems().get(getIndex());
                    onDeleteAction(obj);
                });

                HBox pane = new HBox(10, btnEdit, btnDelete);
                setGraphic(pane);
            }
        });
    }

    private void onEditAction(Customer obj) {
        System.out.println("Editando: " + obj.getNomeCliente());
    }

    private void onDeleteAction(Customer obj) {
        if (Alerts.showConfirmation("Confirmação", "Excluir Cliente", "Tem certeza que deseja excluir " + obj.getNomeCliente() + "?")) {
            try {
                service.deleteById(obj.getIdCliente());
                updateTableView();
            } catch (Exception e) {
                Alerts.showAlert("Erro", "Erro ao excluir", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
}