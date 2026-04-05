package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.services.OrderService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class FiadoListController implements Initializable {

    @FXML private Label labelTitulo;
    @FXML private Label labelTotalDevendo;
    @FXML private TableView<Order> tableFiados;

    // Esses IDs devem bater com o seu FXML
    @FXML private TableColumn<Order, LocalDateTime> columnData;
    @FXML private TableColumn<Order, Integer> columnVendaId;
    @FXML private TableColumn<Order, Double> columnValor;
    @FXML private TableColumn<Order, String> columnStatus;

    private Customer customer;
    private OrderService orderService;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        columnVendaId.setCellValueFactory(new PropertyValueFactory<>("idPedido"));
        columnData.setCellValueFactory(new PropertyValueFactory<>("dataPedido"));
        columnValor.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        columnStatus.setCellValueFactory(new PropertyValueFactory<>("status"));


        columnData.setCellFactory(col -> new TableCell<Order, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(dtf.format(item));
                }
            }
        });

        columnVendaId.setStyle("-fx-alignment: CENTER;");
        columnData.setStyle("-fx-alignment: CENTER;");
        columnValor.setStyle("-fx-alignment: CENTER-RIGHT;");
        columnStatus.setStyle("-fx-alignment: CENTER;");
    }

    public void setCustomerData(Customer customer, OrderService service) {
        this.customer = customer;
        this.orderService = service;
        labelTitulo.setText("Histórico de Fiado: " + customer.getNomeCliente());
        loadFiadoData();
    }

    private void loadFiadoData() {
        if (orderService == null || customer == null) return;

        List<Order> list = orderService.findByCustomerPending(customer.getIdCliente());
        ObservableList<Order> obsList = FXCollections.observableArrayList(list);
        tableFiados.setItems(obsList);

        double total = list.stream().mapToDouble(Order::getValorTotal).sum();
        labelTotalDevendo.setText(String.format("R$ %.2f", total));
    }

    @FXML
    public void onPagarAction() {
        Order selected = tableFiados.getSelectionModel().getSelectedItem();
        if (selected != null) {
            //fazer algo
        }
    }
}