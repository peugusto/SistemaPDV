package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class PaymentController implements Initializable {

    @FXML private Label labelTotalPagamento;
    @FXML private ComboBox<Customer> comboCliente;
    @FXML private ToggleGroup grupoPagamento;
    @FXML private RadioButton radioFiado;
    @FXML private TextField txtValorRecebido;
    @FXML private Label labelTroco;
    @FXML private Button btnConfirmar;
    @FXML private Button btnCancelar;

    private Order order;
    private OrderService orderService;
    private CustomerService customerService;


    public void setDadosVenda(Order order, OrderService orderService, CustomerService customerService) {
        this.order = order;
        this.orderService = orderService;
        this.customerService = customerService;

        labelTotalPagamento.setText(String.format("R$ %.2f", order.getValorTotal()));
        comboCliente.setItems(FXCollections.observableArrayList(customerService.findAll()));
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        radioFiado.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            comboCliente.setDisable(!isSelected);
        });


        txtValorRecebido.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                double recebido = Double.parseDouble(newVal.replace(",", "."));
                double troco = recebido - order.getValorTotal();
                labelTroco.setText(String.format("R$ %.2f", Math.max(0, troco)));
            } catch (NumberFormatException e) {
                labelTroco.setText("R$ 0,00");
            }
        });

        btnConfirmar.setOnAction(event -> realizarBaixa());
        btnCancelar.setOnAction(event -> fecharJanela());
    }

    private void realizarBaixa() {
        try {
            RadioButton selecionado = (RadioButton) grupoPagamento.getSelectedToggle();
            String metodo = selecionado.getText().toUpperCase();

            if (metodo.equals("DINHEIRO")) {
                try {
                    double recebido = Double.parseDouble(txtValorRecebido.getText().replace(",", "."));
                    if (recebido < order.getValorTotal()) {
                        Alerts.showAlert("Valor Insuficiente", null, "O valor recebido é menor que o total da venda!", Alert.AlertType.ERROR);
                        return;
                    }
                } catch (NumberFormatException e) {
                    Alerts.showAlert("Erro", null, "Digite um valor válido para o dinheiro recebido!", Alert.AlertType.ERROR);
                    return;
                }
            }

            if (metodo.equals("FIADO")) {
                Customer c = comboCliente.getValue();
                if (c == null) {
                    Alerts.showAlert("Aviso", null, "Selecione um cliente para venda fiada!", Alert.AlertType.WARNING);
                    return;
                }
                order.setCostumer(c);
                order.setStatus(OrderStatus.FIADO);
            } else {
                order.setCostumer(null);
                order.setStatus(OrderStatus.PAGO);
            }


            orderService.insert(order);

            Alerts.showAlert("Sucesso", null, "Venda finalizada com sucesso!", Alert.AlertType.INFORMATION);
            fecharJanela();

        } catch (Exception e) {
            Alerts.showAlert("Erro", "Erro ao salvar venda", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void fecharJanela() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }
}