package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.MoneyCells;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class FiadoListController implements Initializable {

    @FXML private Label labelTitulo;
    @FXML private Label labelTotalDevendo;
    @FXML private TextField campoValorPagamento;
    @FXML private Button btnPagar;

    @FXML private TableView<OrderItem> tableFiados;
    @FXML private TableColumn<OrderItem, String> columnProduto;
    @FXML private TableColumn<OrderItem, Integer> columnQtd;
    @FXML private TableColumn<OrderItem, BigDecimal> columnPrecoUnit;
    @FXML private TableColumn<OrderItem, BigDecimal> columnSubtotal;
    @FXML private TableColumn<OrderItem, LocalDateTime> columnData;

    private Customer customer;
    private OrderService orderService;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private BigDecimal dividaAtual = Money.ZERO;

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
                new SimpleObjectProperty<>(cellData.getValue().getSubtotal()));
        MoneyCells.format(columnPrecoUnit);
        MoneyCells.format(columnSubtotal);
        Constraints.setTextFieldMoney(campoValorPagamento);

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
        labelTitulo.setText("DETALHAMENTO DE FIADO: " + customer.getNomeCliente().toUpperCase());
        loadFiadoData();
    }

    private void loadFiadoData() {
        if (orderService == null || customer == null) return;

        List<OrderItem> list = orderService.findItemsByCustomerPending(customer.getIdCliente());
        ObservableList<OrderItem> obsList = FXCollections.observableArrayList(list);
        tableFiados.setItems(obsList);

        dividaAtual = orderService.getCustomerDebt(customer.getIdCliente());
        labelTotalDevendo.setText(Money.format(dividaAtual));
    }

    @FXML
    public void onPagarAction() {
        if (customer == null || !Money.isPositive(dividaAtual)) {
            Alerts.showAlert("Aviso", null, "Não existem pendências.", Alert.AlertType.WARNING);
            return;
        }

        BigDecimal valorPago;
        String input = campoValorPagamento.getText();

        if (input == null || input.trim().isEmpty()) {
            valorPago = dividaAtual;
        } else {
            try {
                valorPago = Money.parse(input);
            } catch (NumberFormatException e) {
                Alerts.showAlert("Erro", null, "Valor inválido!", Alert.AlertType.ERROR);
                return;
            }
        }

        if (!Money.isPositive(valorPago) || valorPago.compareTo(dividaAtual) > 0) {
            Alerts.showAlert("Aviso", null, "Valor de pagamento inválido ou maior que a dívida.", Alert.AlertType.WARNING);
            return;
        }

        boolean confirmou = Alerts.showConfirmation(
                "Confirmar Pagamento",
                "Recebimento Parcial / Total",
                String.format("Deseja registrar o pagamento de %s de %s?", Money.format(valorPago), customer.getNomeCliente())
        );

        if (confirmou) {
            try {
                orderService.registerPartialPayment(customer.getIdCliente(), valorPago);
                campoValorPagamento.clear();
                Alerts.showAlert("Sucesso", null, "Pagamento registrado com sucesso!", Alert.AlertType.INFORMATION);
                loadFiadoData();
            } catch (DbException e) {
                Alerts.showAlert("Erro", "Erro ao processar pagamento", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
}