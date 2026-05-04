package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.services.OrderService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class OrderListController implements Initializable {

    @FXML private DatePicker dpDataInicio;
    @FXML private DatePicker dpDataFim;
    @FXML private Button btnFiltrar;
    @FXML private TableView<OrderItem> tableViewItens;
    @FXML private TableColumn<OrderItem, LocalDateTime> columnDataHora;
    @FXML private TableColumn<OrderItem, String> columnCodBarras;
    @FXML private TableColumn<OrderItem, Integer> columnVendaId;
    @FXML private TableColumn<OrderItem, String> columnProduto;
    @FXML private TableColumn<OrderItem, Double> columnPreco;
    @FXML private TableColumn<OrderItem, Integer> columnQtd;
    @FXML private TableColumn<OrderItem, Double> columnTotal;
    @FXML private Label labelFaturamentoTotal;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private ObservableList<OrderItem> obsList = FXCollections.observableArrayList();
    private OrderService service = new OrderService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        dpDataInicio.setValue(LocalDate.now());
        dpDataFim.setValue(LocalDate.now());

        initializeNodes();

        btnFiltrar.setOnAction(event -> atualizarTabela());

        atualizarTabela();
    }

    private void initializeNodes() {
        columnDataHora.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getPedido().getDataPedido()));

        columnDataHora.setCellFactory(col -> new TableCell<OrderItem, LocalDateTime>() {
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

        columnVendaId.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getPedido().getIdPedido()));

        columnCodBarras.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getProduto().getCodBarras()));

        columnProduto.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getProduto().getNomeProduto()));

        columnPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        columnQtd.setCellValueFactory(new PropertyValueFactory<>("qtd"));
        columnTotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        columnProduto.setStyle("-fx-alignment: CENTER-LEFT;");
    }

    public void atualizarTabela() {
        if (service == null) {
            service = new OrderService();
        }

        LocalDate inicio = dpDataInicio.getValue();
        LocalDate fim = dpDataFim.getValue();

        List<OrderItem> list = service.findItemsByDate(inicio, fim);
        obsList = FXCollections.observableArrayList(list);
        tableViewItens.setItems(obsList);

        atualizarFaturamentoTotal();
    }

    private void atualizarFaturamentoTotal() {
        double total = obsList.stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();
        labelFaturamentoTotal.setText(String.format("R$ %.2f", total));
    }
}