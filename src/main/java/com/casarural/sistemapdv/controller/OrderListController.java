package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.services.OrderService;
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
    @FXML private TableColumn<OrderItem, Void> actionsColumn;
    @FXML private Label labelFaturamentoTotal;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private ObservableList<OrderItem> obsList = FXCollections.observableArrayList();
    private OrderService service = new OrderService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        dpDataInicio.setValue(LocalDate.now());
        dpDataFim.setValue(LocalDate.now());

        btnFiltrar.setOnAction(event -> atualizarTabela());
        initializeNodes();
    }

    private void initializeNodes() {

        columnDataHora.setCellValueFactory(new PropertyValueFactory<>("dataPedido"));
        columnVendaId.setCellValueFactory(new PropertyValueFactory<>("idPedido"));
        columnCodBarras.setCellValueFactory(new PropertyValueFactory<>("codBarras"));
        columnProduto.setCellValueFactory(new PropertyValueFactory<>("nomeProduto"));
        columnPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        columnQtd.setCellValueFactory(new PropertyValueFactory<>("qtd"));
        columnTotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        columnProduto.setStyle("-fx-alignment: CENTER;");
    }

    public void atualizarTabela() {
        if (service == null) {
            throw new IllegalStateException("Service estava nulo");
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
