package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
import java.util.Optional;
import java.util.ResourceBundle;

public class FiadoListController implements Initializable {

    @FXML private Label labelTitulo;
    @FXML private Label labelTotalDevendo;

    @FXML private TableView<OrderItem> tableFiados;
    @FXML private TableColumn<OrderItem, String> columnProduto;
    @FXML private TableColumn<OrderItem, Integer> columnQtd;
    @FXML private TableColumn<OrderItem, Double> columnPrecoUnit;
    @FXML private TableColumn<OrderItem, Double> columnSubtotal;
    @FXML private TableColumn<OrderItem, LocalDateTime> columnData;

    private Customer customer;
    private OrderService orderService;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {

        columnProduto.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getProduto().getNomeProduto()));

        columnQtd.setCellValueFactory(new PropertyValueFactory<>("qtd"));

        columnPrecoUnit.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));


        columnSubtotal.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getQtd() * cellData.getValue().getPrecoUnitario()));


        columnData.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getPedido().getDataPedido()));


        columnData.setCellFactory(col -> new TableCell<OrderItem, LocalDateTime>() {
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


        columnProduto.setStyle("-fx-alignment: CENTER-LEFT;");
        columnQtd.setStyle("-fx-alignment: CENTER;");
        columnPrecoUnit.setStyle("-fx-alignment: CENTER-RIGHT;");
        columnSubtotal.setStyle("-fx-alignment: CENTER-RIGHT;");
        columnData.setStyle("-fx-alignment: CENTER;");
    }

    public void setCustomerData(Customer customer, OrderService service) {
        this.customer = customer;
        this.orderService = service;
        labelTitulo.setText("Histórico de Itens (Fiado): " + customer.getNomeCliente());
        loadFiadoData();
    }

    private void loadFiadoData() {
        if (orderService == null || customer == null) return;


        List<OrderItem> list = orderService.findItemsByCustomerPending(customer.getIdCliente());

        ObservableList<OrderItem> obsList = FXCollections.observableArrayList(list);
        tableFiados.setItems(obsList);


        double total = list.stream()
                .mapToDouble(i -> i.getQtd() * i.getPrecoUnitario())
                .sum();

        labelTotalDevendo.setText(String.format("Total Pendente: R$ %.2f", total));
    }

    @FXML
    public void onPagarAction() {
        if (customer == null || tableFiados.getItems().isEmpty()) {
            Alerts.showAlert("Aviso", null, "Não existem pendências.", Alert.AlertType.WARNING);
            return;
        }

        boolean confirmou = Alerts.showConfirmation(
                "Confirmar Pagamento Total",
                "Liquidação de Dívida",
                "Deseja confirmar o recebimento de " + labelTotalDevendo.getText() +
                        " de " + customer.getNomeCliente() + "?"
        );

        if (confirmou) {
            try {
                orderService.payFullDebt(customer.getIdCliente());
                Alerts.showAlert("Sucesso", null, "Dívida quitada!", Alert.AlertType.INFORMATION);
                loadFiadoData();
            } catch (DbException e) {
                Alerts.showAlert("Erro", "Erro ao pagar", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
}