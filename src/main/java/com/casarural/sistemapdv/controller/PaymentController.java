package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
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

    @FXML private ToggleButton btnDinheiro;
    @FXML private ToggleButton btnCartao;
    @FXML private ToggleButton btnPix;
    @FXML private ToggleButton btnFiado;

    @FXML private TextField txtValorRecebido;
    @FXML private Label labelTroco;
    @FXML private Button btnConfirmar;
    @FXML private Button btnCancelar;

    private Order order;
    private OrderService orderService;
    private CustomerService customerService;
    private double saldoRestante;

    public void setDadosVenda(Order order, OrderService orderService, CustomerService customerService) {
        this.order = order;
        this.orderService = orderService;
        this.customerService = customerService;
        this.saldoRestante = order.getValorTotal();

        atualizarExibicao();
        comboCliente.setItems(FXCollections.observableArrayList(customerService.findAll()));
    }

    private void atualizarExibicao() {
        labelTotalPagamento.setText(String.format("R$ %.2f", Math.max(0, saldoRestante)));
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarVisualBotoesPagamento();

        btnFiado.selectedProperty().addListener((obs, was, isSelected) -> {
            comboCliente.setDisable(!isSelected);
        });

        txtValorRecebido.textProperty().addListener((obs, oldVal, newVal) -> {
            if (btnDinheiro.isSelected()) {
                try {
                    double recebido = Double.parseDouble(newVal.replace(",", "."));
                    labelTroco.setText(String.format("R$ %.2f", Math.max(0, recebido - saldoRestante)));
                } catch (NumberFormatException e) {
                    labelTroco.setText("R$ 0,00");
                }
            } else {
                labelTroco.setText("R$ 0,00");
            }
        });

        txtValorRecebido.setOnAction(event -> adicionarPagamentoManual());
        btnConfirmar.setOnAction(event -> realizarBaixaAutomatica());
        btnCancelar.setOnAction(event -> fecharJanela());
    }

    private void configurarVisualBotoesPagamento() {
        ToggleButton[] botoes = {btnDinheiro, btnCartao, btnPix, btnFiado};
        String estiloBase = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-border-radius: 8; ";

        for (ToggleButton btn : botoes) {
            btn.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    btn.setStyle(estiloBase + "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-border-color: #27ae60;");
                } else {
                    btn.setStyle(estiloBase + "-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.2);");
                }
            });

            if (btn.isSelected()) {
                btn.setStyle(estiloBase + "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-border-color: #27ae60;");
            } else {
                btn.setStyle(estiloBase + "-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: white; -fx-border-color: rgba(255,255,255,0.2);");
            }
        }
    }

    private void adicionarPagamentoManual() {
        try {
            double valorInput = Double.parseDouble(txtValorRecebido.getText().replace(",", "."));
            processarPagamento(valorInput);
        } catch (NumberFormatException e) {
            Alerts.showAlert("Erro", null, "Valor inválido!", Alert.AlertType.ERROR);
        }
    }

    private void realizarBaixaAutomatica() {
        if (saldoRestante > 0.009) {
            processarPagamento(saldoRestante);
        } else {
            salvarVenda();
        }
    }

    private void processarPagamento(double valor) {
        ToggleButton selecionado = (ToggleButton) grupoPagamento.getSelectedToggle();
        String textoOriginal = selecionado.getText().toUpperCase();

        String textoNormalizado = java.text.Normalizer.normalize(textoOriginal, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        PaymentMethod metodo = PaymentMethod.valueOf(textoNormalizado);

        if (metodo != PaymentMethod.DINHEIRO && valor > saldoRestante + 0.009) {
            Alerts.showAlert("Aviso", null, "Valor excede o saldo!", Alert.AlertType.WARNING);
            return;
        }

        if (metodo == PaymentMethod.FIADO) {
            Customer clienteSelecionado = comboCliente.getValue();

            if (clienteSelecionado == null) {
                Alerts.showAlert("Aviso", null, "Selecione o cliente para a venda no Fiado!", Alert.AlertType.WARNING);
                return;
            }

            if (clienteSelecionado.getSituacaoFiado() == CustomerStatus.BLOQUEADO) {
                Alerts.showAlert("Venda Recusada", "Operação não autorizada",
                        String.format("O cliente '%s' está com o cadastro BLOQUEADO para compras fiadas.",
                                clienteSelecionado.getNomeCliente()), Alert.AlertType.ERROR);
                return;
            }
        }

        double valorEfetivo = Math.min(valor, saldoRestante);
        order.getPagamentos().add(new Order.PaymentSlice(valorEfetivo, metodo));

        saldoRestante -= valorEfetivo;
        txtValorRecebido.clear();
        atualizarExibicao();

        if (saldoRestante <= 0.009) {
            salvarVenda();
        }
    }

    private void salvarVenda() {
        try {
            double totalFiado = order.getPagamentos().stream()
                    .filter(p -> p.getMetodo() == PaymentMethod.FIADO)
                    .mapToDouble(Order.PaymentSlice::getValor)
                    .sum();

            if (totalFiado > 0) {
                order.setCostumer(comboCliente.getValue());
                order.setStatus(OrderStatus.FIADO);
                order.setValorTotal(totalFiado);
            } else {
                order.setStatus(OrderStatus.PAGO);
            }

            orderService.insert(order);
            Alerts.showAlert("Sucesso", null, "Venda finalizada!", Alert.AlertType.INFORMATION);
            fecharJanela();
        } catch (Exception e) {
            Alerts.showAlert("Erro", "Erro ao salvar", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void fecharJanela() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }
}