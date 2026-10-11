package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.RegraFiado;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

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
    /** Produtos sem fiado que o operador escolheu tirar da venda. */
    private final List<OrderItem> itensRetirados = new ArrayList<>();

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

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

    /**
     * A aparência das formas de pagamento (selecionada ou não) vem do componentes.css (.opcao-grande).
     * Aqui só garantimos que sempre haja uma forma escolhida: clicar na já selecionada não a desmarca.
     */
    private void configurarVisualBotoesPagamento() {
        grupoPagamento.selectedToggleProperty().addListener((obs, antiga, nova) -> {
            if (nova == null && antiga != null) {
                antiga.setSelected(true);
            }
        });
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

            // Bloqueio automático: compra no fiado com mais de RegraFiado.PRAZO_DIAS dias sem pagar
            if (clienteSelecionado.isBloqueadoPorAtraso()) {
                log.info("Fiado recusado para {}: {} em atraso", clienteSelecionado.getNomeCliente(),
                        Money.format(clienteSelecionado.getValorEmAtraso()));
                Alerts.showAlert("Fiado bloqueado", "Conta bloqueada por atraso",
                        String.format("%s tem compras no fiado com mais de %d dias sem pagar.%n%n"
                                        + "Para liberar o fiado, ele precisa pagar %s.%n"
                                        + "Esta compra pode ser paga em dinheiro, PIX ou cartão.",
                                clienteSelecionado.getNomeCliente(), RegraFiado.PRAZO_DIAS,
                                Money.format(clienteSelecionado.getValorEmAtraso())),
                        Alert.AlertType.WARNING);
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

            // Produtos que não podem ser fiados (cigarro, bebida...): o fiado só cobre os demais
            if (valorFiado.compareTo(order.getFiadoAindaPermitido()) > 0) {
                if (!tirarItensSemFiado(clienteSelecionado, disponivel)) {
                    return;
                }
                // Itens tirados da venda: o que sobrou vai inteiro para o fiado
                valorFiado = saldoRestante;
            }

            if (valorFiado.compareTo(disponivel) > 0) {
                Alerts.showAlert("Venda Recusada", "Limite de crédito excedido",
                        String.format("O cliente '%s' tem limite de %s e deve %s.%nDisponível para fiado: %s.",
                                clienteSelecionado.getNomeCliente(), Money.format(clienteSelecionado.getLimiteCredito()),
                                Money.format(devendo.add(fiadoNestaVenda)), Money.format(Money.max(Money.ZERO, disponivel))),
                        Alert.AlertType.ERROR);
                return;
            }

            clienteFiado = clienteSelecionado;
            valor = valorFiado;
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

    /**
     * A venda tem produtos que não podem ser fiados e o fiado tentou cobri-los.
     * Mostra quais são e oferece tirar esses produtos da venda e lançar o resto no fiado.
     * Retorna true se os itens foram tirados (o saldo passa a ser só o dos outros produtos).
     */
    private boolean tirarItensSemFiado(Customer cliente, BigDecimal limiteDisponivel) {
        List<OrderItem> semFiado = order.getItensSemFiado();
        String lista = semFiado.stream()
                .map(i -> String.format("  •  %d x %s  (%s)", i.getQtd(), i.getNomeProduto(), Money.format(i.getSubtotal())))
                .collect(Collectors.joining("\n"));

        if (order.getTotalPermitidoNoFiado().signum() == 0) {
            Alerts.showAlertAndWait("Fiado não permitido", "Nenhum produto desta venda pode ser vendido no fiado:",
                    lista + "\n\nCobre em dinheiro, PIX ou cartão.", Alert.AlertType.WARNING);
            return false;
        }

        BigDecimal totalSemFiado = semFiado.stream().map(OrderItem::getSubtotal).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal restoParaFiado = order.getValorTotal().subtract(totalSemFiado)
                .subtract(order.getTotalPagoNaHora()).subtract(totalPorMetodo(PaymentMethod.FIADO));

        if (restoParaFiado.signum() < 0) {
            Alerts.showAlertAndWait("Fiado não permitido", "Estes produtos não podem ser vendidos no fiado:",
                    lista + "\n\nOs pagamentos já lançados passam do valor dos outros produtos. "
                            + "Cancele o pagamento e comece de novo.", Alert.AlertType.WARNING);
            return false;
        }
        // Confere o limite antes de mexer na venda: se não couber, nada é tirado
        if (restoParaFiado.compareTo(limiteDisponivel) > 0) {
            Alerts.showAlertAndWait("Venda Recusada", "Limite de crédito excedido",
                    String.format("Mesmo sem esses produtos, o fiado seria de %s, e o cliente '%s' só tem %s disponível.",
                            Money.format(restoParaFiado), cliente.getNomeCliente(), Money.format(Money.max(Money.ZERO, limiteDisponivel))),
                    Alert.AlertType.ERROR);
            return false;
        }

        int escolha = Alerts.showChoice("Fiado não permitido",
                "Estes produtos não podem ser vendidos no fiado:",
                lista + "\n\n"
                        + "Para o cliente levar esses produtos, clique em Voltar e cobre "
                        + Money.format(order.getFaltaPagarNaHoraParaItensSemFiado()) + " em dinheiro, PIX ou cartão antes do fiado.\n\n"
                        + "Ou tire esses produtos da venda e lance o restante (" + Money.format(restoParaFiado)
                        + ") no fiado de " + cliente.getNomeCliente() + ".",
                Alert.AlertType.WARNING,
                "Voltar",
                "Tirar da venda e lançar " + Money.format(restoParaFiado) + " no fiado");
        if (escolha != 0) {
            return false;
        }

        itensRetirados.addAll(semFiado);
        order.removerItens(semFiado);
        saldoRestante = order.getSaldo();
        atualizarExibicao();
        log.info("Itens sem fiado tirados da venda de {}: {}", cliente.getNomeCliente(),
                semFiado.stream().map(OrderItem::getNomeProduto).collect(Collectors.joining(", ")));
        return true;
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
            if (itensRetirados.isEmpty()) {
                Alerts.showAlert("Sucesso", null, "Venda finalizada!", Alert.AlertType.INFORMATION);
            } else {
                Alerts.showAlert("Venda finalizada", "Estes produtos foram tirados da venda. Devolva-os à prateleira:",
                        itensRetirados.stream().map(i -> "  •  " + i.getQtd() + " x " + i.getNomeProduto())
                                .collect(Collectors.joining("\n")),
                        Alert.AlertType.INFORMATION);
            }
            fecharJanela();
        } catch (Exception e) {
            Alerts.showAlert("Erro", "Erro ao salvar", e.getMessage(), Alert.AlertType.ERROR);
            reiniciarPagamentos();
        }
    }

    private BigDecimal totalPorMetodo(PaymentMethod metodo) {
        return order.getTotalPagamentos(metodo);
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