package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.RegraDesconto;
import com.casarural.sistemapdv.model.entities.RegraFiado;
import com.casarural.sistemapdv.model.entities.RegraTroca;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.services.AcessoRelatoriosService;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Tema;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @FXML private Button btnDesconto;
    @FXML private Label labelResumo;
    @FXML private Label labelTroca;

    private Order order;
    private OrderService orderService;
    private CustomerService customerService;
    private BigDecimal saldoRestante = Money.ZERO;
    private Customer clienteFiado;
    private boolean vendaConcluida = false;
    /** Produtos sem fiado que o operador escolheu tirar da venda. */
    private final List<OrderItem> itensRetirados = new ArrayList<>();
    /** Troca: a venda antiga; o que foi pago nela vira crédito nesta. */
    private VendaParaDesfazer trocaDe;
    /** Troca: crédito que sobra e volta para o cliente (por forma). */
    private Map<PaymentMethod, BigDecimal> devolverTroca = new EnumMap<>(PaymentMethod.class);
    private final AcessoRelatoriosService acesso = new AcessoRelatoriosService(DaoFactory.createConfiguracaoDAO());

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    public boolean isVendaConcluida() {
        return vendaConcluida;
    }

    public void setDadosVenda(Order order, OrderService orderService, CustomerService customerService) {
        this.order = order;
        this.orderService = orderService;
        this.customerService = customerService;
        this.saldoRestante = order.getSaldo();

        atualizarExibicao();
        comboCliente.setItems(FXCollections.observableArrayList(customerService.findAll()));
    }

    /**
     * Troca: o que o cliente pagou na venda antiga entra como crédito, nas mesmas formas.
     * Se havia fiado, a venda nova fica na conta do mesmo cliente.
     */
    public void setTroca(VendaParaDesfazer venda) {
        this.trocaDe = venda;
        aplicarCreditos();
        if (Money.isPositive(venda.getPagoPorForma().getOrDefault(PaymentMethod.FIADO, Money.ZERO)) && venda.getIdCliente() != null) {
            comboCliente.getItems().stream()
                    .filter(c -> c.getIdCliente() == venda.getIdCliente())
                    .findFirst()
                    .ifPresent(c -> {
                        clienteFiado = c;
                        comboCliente.setValue(c);
                    });
        }
        atualizarExibicao();
    }

    /** (Re)calcula o crédito da troca para o total atual; os pagamentos novos continuam. */
    private void aplicarCreditos() {
        order.getPagamentos().removeIf(Order.PaymentSlice::isCredito);
        devolverTroca = new EnumMap<>(PaymentMethod.class);
        if (trocaDe != null) {
            RegraTroca.Resultado r = RegraTroca.calcular(trocaDe.getPagoPorForma(), order.getValorTotal());
            order.getPagamentos().addAll(0, r.getCreditos());
            devolverTroca = r.getDevolver();
        }
        saldoRestante = order.getSaldo();
    }

    private void atualizarExibicao() {
        labelTotalPagamento.setText(Money.format(Money.max(Money.ZERO, saldoRestante)));

        // Compra R$ 20,00 · desconto R$ 2,00 (10%) · total R$ 18,00 · já lançado R$ 5,00
        List<String> partes = new ArrayList<>();
        BigDecimal desconto = order.getDesconto();
        if (Money.isPositive(desconto)) {
            partes.add("Compra " + Money.format(order.getSubtotalItens()));
            partes.add("desconto " + Money.format(desconto) + " (" + RegraDesconto.percentual(desconto, order.getSubtotalItens()) + ")");
        }
        partes.add("total " + Money.format(order.getValorTotal()));
        BigDecimal lancado = order.getPagamentos().stream().filter(p -> !p.isCredito())
                .map(Order.PaymentSlice::getValor).reduce(Money.ZERO, BigDecimal::add);
        if (Money.isPositive(lancado)) partes.add("já lançado " + Money.format(lancado));
        String resumo = String.join("  ·  ", partes);
        labelResumo.setText(resumo.substring(0, 1).toUpperCase() + resumo.substring(1));

        boolean troca = trocaDe != null;
        labelTroca.setVisible(troca);
        labelTroca.setManaged(troca);
        if (troca) {
            BigDecimal credito = order.getPagamentos().stream().filter(Order.PaymentSlice::isCredito)
                    .map(Order.PaymentSlice::getValor).reduce(Money.ZERO, BigDecimal::add);
            String texto = "Troca da venda #" + trocaDe.getIdPedido() + ": crédito de " + Money.format(credito);
            BigDecimal fiadoAntes = trocaDe.getPagoPorForma().getOrDefault(PaymentMethod.FIADO, Money.ZERO);
            BigDecimal fiadoAgora = order.getPagamentos().stream()
                    .filter(p -> p.isCredito() && p.getMetodo() == PaymentMethod.FIADO)
                    .map(Order.PaymentSlice::getValor).reduce(Money.ZERO, BigDecimal::add);
            if (Money.isPositive(fiadoAntes.subtract(fiadoAgora))) {
                texto += "  ·  " + Money.format(fiadoAntes.subtract(fiadoAgora)) + " a menos no fiado";
            }
            if (!devolverTroca.isEmpty()) {
                texto += "  ·  Devolver ao cliente: " + RegraTroca.descreverDevolucao(devolverTroca);
            }
            labelTroca.setText(texto);
        }
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
        btnDesconto.setOnAction(event -> onDesconto());
        btnDesconto.sceneProperty().addListener((obs, antiga, cena) -> {
            if (cena != null) {
                cena.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.F4) {
                        onDesconto();
                        e.consume();
                    }
                });
            }
        });
    }

    // ===================== Desconto (sempre com PIN) =====================

    private void onDesconto() {
        if (order.temPagamentoNovo()) {
            Alerts.showAlert("Desconto", null,
                    "O desconto é dado antes de lançar os pagamentos. Clique em Voltar (Esc) e finalize a venda de novo.",
                    Alert.AlertType.WARNING);
            return;
        }
        if (!PinDialogo.liberar(acesso, btnDesconto.getScene().getWindow(), "Desconto", "dar desconto")) return;

        Optional<BigDecimal> desconto = pedirDesconto();
        if (desconto.isEmpty()) return;

        order.aplicarDesconto(desconto.get());
        log.info("Desconto de {} numa venda de {}", Money.format(order.getDesconto()), Money.format(order.getSubtotalItens()));
        aplicarCreditos();
        atualizarExibicao();
        txtValorRecebido.requestFocus();
    }

    /** Janela do desconto: valor, R$ ou %, e o total que fica. Zero tira o desconto. */
    private Optional<BigDecimal> pedirDesconto() {
        BigDecimal subtotal = order.getSubtotalItens();
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Desconto");
        dialog.setHeaderText("Compra de " + Money.format(subtotal));

        TextField campo = new TextField();
        campo.setPromptText("0");
        campo.getStyleClass().add("campo-grande");
        Constraints.setTextFieldMoney(campo);
        ToggleGroup tipo = new ToggleGroup();
        ToggleButton emReais = new ToggleButton("R$");
        ToggleButton emPorcento = new ToggleButton("%");
        for (ToggleButton b : new ToggleButton[]{emReais, emPorcento}) {
            b.setToggleGroup(tipo);
            b.getStyleClass().add("pilula");
            b.setFocusTraversable(false);
        }
        emReais.setSelected(true);
        tipo.selectedToggleProperty().addListener((o, antes, agora) -> { if (agora == null) antes.setSelected(true); });
        Label previa = new Label();
        previa.getStyleClass().add("valor-grande");
        Label erro = new Label();
        erro.getStyleClass().add("texto-perigo");
        erro.setWrapText(true);
        erro.setMaxWidth(360);

        javafx.scene.layout.HBox linha = new javafx.scene.layout.HBox(8, campo, emReais, emPorcento);
        linha.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.VBox conteudo = new javafx.scene.layout.VBox(10,
                new Label("Digite o desconto (deixe 0 para tirar):"), linha, previa, erro);
        dialog.getDialogPane().setContent(conteudo);

        ButtonType aplicar = new ButtonType("Aplicar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(aplicar, new ButtonType("Voltar", ButtonBar.ButtonData.CANCEL_CLOSE));
        javafx.scene.Node botao = dialog.getDialogPane().lookupButton(aplicar);

        // Prévia: total com desconto, ou o motivo de não aceitar
        BigDecimal[] calculado = {Money.ZERO};
        Runnable atualizar = () -> {
            String texto = campo.getText() == null ? "" : campo.getText().trim();
            try {
                calculado[0] = texto.isEmpty() || Money.parse(texto).signum() == 0
                        ? Money.ZERO : RegraDesconto.calcular(subtotal, texto, emPorcento.isSelected());
                previa.setText("Total: " + Money.format(subtotal.subtract(calculado[0]))
                        + (Money.isPositive(calculado[0]) ? "  (desconto " + Money.format(calculado[0]) + ")" : ""));
                erro.setText("");
                botao.setDisable(false);
            } catch (IllegalArgumentException e) {
                previa.setText("");
                erro.setText(e.getMessage());
                botao.setDisable(true);
            }
        };
        campo.textProperty().addListener((o, a, b) -> atualizar.run());
        tipo.selectedToggleProperty().addListener((o, a, b) -> atualizar.run());
        if (Money.isPositive(order.getDesconto())) campo.setText(Money.toInput(order.getDesconto()));
        atualizar.run();

        dialog.setResultConverter(b -> b == aplicar ? calculado[0] : null);
        Tema.aplicar(dialog.getDialogPane());
        javafx.application.Platform.runLater(campo::requestFocus);
        return dialog.showAndWait();
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
        if (!Money.isPositive(saldoRestante)) {
            salvarVenda();
            return;
        }
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
            if (trocaDe != null && trocaDe.getIdCliente() != null && trocaDe.getIdCliente() == clienteSelecionado.getIdCliente()) {
                devendo = devendo.subtract(trocaDe.getPagoPorForma().getOrDefault(PaymentMethod.FIADO, Money.ZERO));
            }
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
        aplicarCreditos();
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
            if (trocaDe != null) {
                log.warn("Troca: venda #{} substituída pela #{}. Devolvido: {}", trocaDe.getIdPedido(), order.getIdPedido(), devolverTroca);
                Alerts.showAlertAndWait("Troca concluída", "Venda #" + trocaDe.getIdPedido() + " trocada pela #" + order.getIdPedido(),
                        devolverTroca.isEmpty() ? "Os produtos devolvidos voltaram para o estoque."
                                : "Devolva ao cliente: " + RegraTroca.descreverDevolucao(devolverTroca) + ".",
                        Alert.AlertType.INFORMATION);
            } else if (itensRetirados.isEmpty()) {
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
        Customer clienteDaTroca = clienteFiado != null && trocaDe != null
                && trocaDe.getIdCliente() != null && trocaDe.getIdCliente() == clienteFiado.getIdCliente() ? clienteFiado : null;
        clienteFiado = clienteDaTroca;
        aplicarCreditos();
        txtValorRecebido.clear();
        atualizarExibicao();
    }

    private void fecharJanela() {
        ((Stage) btnCancelar.getScene().getWindow()).close();
    }
}