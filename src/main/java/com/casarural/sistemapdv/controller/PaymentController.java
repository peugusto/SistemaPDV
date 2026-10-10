package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
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
    private BigDecimal saldoRestante = Money.ZERO;
    private Customer clienteFiado;
    private boolean vendaConcluida = false;

    public boolean isVendaConcluida() {
        return vendaConcluida;
    }

    public void setDadosVenda(Order order, OrderService orderService, CustomerService customerService) {
        this.order = order;
        this.orderService = orderService;
        this.customerService = customerService;
        this.saldoRestante = order.getValorTotal();

        atualizarExibicao();
        comboCliente.setItems(FXCollections.observableArrayList(customerService.findAll()));
    }

    private void atualizarExibicao() {
        labelTotalPagamento.setText(Money.format(Money.max(Money.ZERO, saldoRestante)));
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarVisualBotoesPagamento();
        Constraints.setTextFieldMoney(txtValorRecebido);

        btnFiado.selectedProperty().addListener((obs, was, isSelected) -> {
            comboCliente.setDisable(!isSelected);
        });

        txtValorRecebido.textProperty().addListener((obs, oldVal, newVal) -> {
            if (btnDinheiro.isSelected()) {
                try {
                    BigDecimal recebido = Money.parse(newVal);
                    labelTroco.setText(Money.format(Money.max(Money.ZERO, recebido.subtract(saldoRestante))));
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
            BigDecimal valorInput = Money.parse(txtValorRecebido.getText());
            if (!Money.isPositive(valorInput)) {
                Alerts.showAlert("Aviso", null, "Informe um valor maior que zero.", Alert.AlertType.WARNING);
                return;
            }
            processarPagamento(valorInput);
        } catch (NumberFormatException e) {
            Alerts.showAlert("Erro", null, "Valor inválido!", Alert.AlertType.ERROR);
        }
    }

    private void realizarBaixaAutomatica() {
        if (Money.isPositive(saldoRestante)) {
            processarPagamento(saldoRestante);
        } else {
            salvarVenda();
        }
    }

    private void processarPagamento(BigDecimal valor) {
        ToggleButton selecionado = (ToggleButton) grupoPagamento.getSelectedToggle();
        if (selecionado == null) {
            Alerts.showAlert("Aviso", null, "Selecione a forma de pagamento.", Alert.AlertType.WARNING);
            return;
        }
        String textoOriginal = selecionado.getText().toUpperCase();

        String textoNormalizado = java.text.Normalizer.normalize(textoOriginal, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        PaymentMethod metodo = PaymentMethod.valueOf(textoNormalizado);

        if (metodo != PaymentMethod.DINHEIRO && valor.compareTo(saldoRestante) > 0) {
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

            if (clienteFiado != null && clienteFiado.getIdCliente() != clienteSelecionado.getIdCliente()) {
                Alerts.showAlert("Aviso", null,
                        String.format("Esta venda já possui fiado para o cliente '%s'.", clienteFiado.getNomeCliente()),
                        Alert.AlertType.WARNING);
                return;
            }

            BigDecimal devendo = Money.of(clienteSelecionado.getTotalDevendo());
            BigDecimal fiadoNestaVenda = totalPorMetodo(PaymentMethod.FIADO);
            BigDecimal valorFiado = Money.min(valor, saldoRestante);
            BigDecimal disponivel = clienteSelecionado.getLimiteCredito().subtract(devendo).subtract(fiadoNestaVenda);

            if (valorFiado.compareTo(disponivel) > 0) {
                Alerts.showAlert("Venda Recusada", "Limite de crédito excedido",
                        String.format("O cliente '%s' tem limite de %s e deve %s.%nDisponível para fiado: %s.",
                                clienteSelecionado.getNomeCliente(), Money.format(clienteSelecionado.getLimiteCredito()),
                                Money.format(devendo.add(fiadoNestaVenda)), Money.format(Money.max(Money.ZERO, disponivel))),
                        Alert.AlertType.ERROR);
                return;
            }

            clienteFiado = clienteSelecionado;
        }

        // No dinheiro o cliente pode entregar mais que o saldo: registra só o saldo, o resto é troco
        BigDecimal valorEfetivo = Money.min(valor, saldoRestante);
        order.getPagamentos().add(new Order.PaymentSlice(valorEfetivo, metodo));

        saldoRestante = saldoRestante.subtract(valorEfetivo);
        txtValorRecebido.clear();
        atualizarExibicao();

        if (saldoRestante.signum() == 0) {
            salvarVenda();
        }
    }

    private void salvarVenda() {
        try {
            BigDecimal totalFiado = totalPorMetodo(PaymentMethod.FIADO);

            // valor_total guarda sempre o total da venda; a dívida é valor_total - pagamentos não-fiado
            if (Money.isPositive(totalFiado)) {
                order.setCostumer(clienteFiado);
                order.setStatus(OrderStatus.FIADO);
            } else {
                order.setStatus(OrderStatus.PAGO);
            }

            orderService.insert(order);
            vendaConcluida = true;
            Alerts.showAlert("Sucesso", null, "Venda finalizada!", Alert.AlertType.INFORMATION);
            fecharJanela();
        } catch (Exception e) {
            Alerts.showAlert("Erro", "Erro ao salvar", e.getMessage(), Alert.AlertType.ERROR);
            reiniciarPagamentos();
        }
    }

    private BigDecimal totalPorMetodo(PaymentMethod metodo) {
        return order.getPagamentos().stream()
                .filter(p -> p.getMetodo() == metodo)
                .map(Order.PaymentSlice::getValor)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private void reiniciarPagamentos() {
        order.getPagamentos().clear();
        saldoRestante = order.getValorTotal();
        clienteFiado = null;
        txtValorRecebido.clear();
        atualizarExibicao();
    }

    private void fecharJanela() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }
}