package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.model.entities.RegraFiado;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.RegraTroca;
import com.casarural.sistemapdv.services.AcessoRelatoriosService;
import com.casarural.sistemapdv.services.CashRegisterService;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.MoneyCells;
import com.casarural.sistemapdv.util.Tema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Conta de fiado de um cliente: compras em aberto (com o que já foi pago de cada uma),
 * pagamentos recebidos, compras quitadas e o recebimento de novos pagamentos.
 */
public class FiadoListController implements Initializable {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Logger log = LoggerFactory.getLogger(FiadoListController.class);

    @FXML private Label labelTitulo;
    @FXML private Label labelSituacao;
    @FXML private Label labelTotalDevendo;

    // Compras em aberto
    @FXML private TableView<Order> tableAbertos;
    @FXML private TableColumn<Order, LocalDateTime> colAbertoData;
    @FXML private TableColumn<Order, String> colAbertoPedido;
    @FXML private TableColumn<Order, Order> colAbertoVence;
    @FXML private TableColumn<Order, BigDecimal> colAbertoTotal;
    @FXML private TableColumn<Order, BigDecimal> colAbertoPagoVenda;
    @FXML private TableColumn<Order, BigDecimal> colAbertoPagoDepois;
    @FXML private TableColumn<Order, BigDecimal> colAbertoFalta;
    @FXML private Label labelItensAberto;
    @FXML private TableView<OrderItem> tableItensAberto;
    @FXML private TableColumn<OrderItem, String> colItemAbertoProduto;
    @FXML private TableColumn<OrderItem, Integer> colItemAbertoQtd;
    @FXML private TableColumn<OrderItem, BigDecimal> colItemAbertoPreco;
    @FXML private TableColumn<OrderItem, BigDecimal> colItemAbertoSubtotal;

    // Pagamentos recebidos
    @FXML private TableView<Payment> tableRecebimentos;
    @FXML private TableColumn<Payment, LocalDateTime> colRecData;
    @FXML private TableColumn<Payment, String> colRecForma;
    @FXML private TableColumn<Payment, BigDecimal> colRecValor;
    @FXML private TableColumn<Payment, String> colRecPedidos;
    @FXML private Button btnEstornar;
    @FXML private Label labelEstornoDica;
    private final AcessoRelatoriosService acesso = new AcessoRelatoriosService(DaoFactory.createConfiguracaoDAO());
    /** Caixa aberto agora (null = fechado): só recebimentos dele podem ser estornados. */
    private Integer idCaixaAberto;

    // Compras quitadas
    @FXML private TableView<Order> tableQuitados;
    @FXML private TableColumn<Order, LocalDateTime> colQuitData;
    @FXML private TableColumn<Order, String> colQuitPedido;
    @FXML private TableColumn<Order, BigDecimal> colQuitTotal;
    @FXML private Label labelItensQuitado;
    @FXML private TableView<OrderItem> tableItensQuitado;
    @FXML private TableColumn<OrderItem, String> colItemQuitProduto;
    @FXML private TableColumn<OrderItem, Integer> colItemQuitQtd;
    @FXML private TableColumn<OrderItem, BigDecimal> colItemQuitPreco;
    @FXML private TableColumn<OrderItem, BigDecimal> colItemQuitSubtotal;

    // Receber pagamento
    @FXML private ToggleGroup grupoForma;
    @FXML private ToggleButton btnFormaDinheiro;
    @FXML private ToggleButton btnFormaPix;
    @FXML private ToggleButton btnFormaCartao;
    @FXML private TextField campoValorPagamento;
    @FXML private Button btnPagar;

    private Customer customer;
    private OrderService orderService;
    private BigDecimal dividaAtual = Money.ZERO;
    private boolean recebendo;
    private ImpressaoVendaService impressaoVendaService = new ImpressaoVendaService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarAbertos();
        configurarRecebimentos();
        configurarQuitados();
        configurarRecebimento();
    }

    private void configurarAbertos() {
        colAbertoData.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDataPedido()));
        formatarDataHora(colAbertoData);
        colAbertoPedido.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getIdPedido()));
        // Vencimento (compra + prazo); atrasada em vermelho: é ela que bloqueia o fiado
        colAbertoVence.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colAbertoVence.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Order compra, boolean empty) {
                super.updateItem(compra, empty);
                if (empty || compra == null) {
                    setText(null);
                    Tema.estado(this, null);
                    return;
                }
                boolean atrasada = RegraFiado.estaAtrasada(compra, LocalDateTime.now());
                setText(DATA.format(RegraFiado.vencimento(compra)) + (atrasada ? "  (atrasada)" : ""));
                Tema.estado(this, atrasada ? "texto-perigo" : null);
            }
        });
        colAbertoTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        colAbertoPagoVenda.setCellValueFactory(new PropertyValueFactory<>("pagoNaVenda"));
        colAbertoPagoDepois.setCellValueFactory(new PropertyValueFactory<>("recebidoDepois"));
        colAbertoFalta.setCellValueFactory(new PropertyValueFactory<>("falta"));
        MoneyCells.format(colAbertoTotal);
        MoneyCells.format(colAbertoPagoVenda);
        MoneyCells.format(colAbertoPagoDepois);
        MoneyCells.format(colAbertoFalta);
        colAbertoFalta.getStyleClass().add("texto-perigo");

        configurarItens(colItemAbertoProduto, colItemAbertoQtd, colItemAbertoPreco, colItemAbertoSubtotal);
        tableAbertos.getSelectionModel().selectedItemProperty().addListener((obs, antigo, pedido) ->
                mostrarItens(pedido, tableItensAberto, labelItensAberto));
    }

    private void configurarRecebimentos() {
        colRecData.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDataPagamento()));
        formatarDataHora(colRecData);
        colRecForma.setCellValueFactory(c -> new SimpleStringProperty(nomeDaForma(c.getValue().getMetodo())));
        colRecValor.setCellValueFactory(new PropertyValueFactory<>("valorPago"));
        MoneyCells.format(colRecValor);
        colRecPedidos.setCellValueFactory(new PropertyValueFactory<>("pedidos"));
        tableRecebimentos.getSelectionModel().selectedItemProperty().addListener((obs, antes, rec) -> {
            boolean pode = rec != null && idCaixaAberto != null && idCaixaAberto.equals(rec.getIdCaixa());
            btnEstornar.setDisable(!pode);
            labelEstornoDica.setText(rec != null && !pode
                    ? "Este recebimento é de um caixa já fechado: não dá para estornar."
                    : "Recebeu errado? Selecione o pagamento e clique em Estornar (só os do caixa aberto).");
        });
    }

    private void configurarQuitados() {
        colQuitData.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDataPedido()));
        formatarDataHora(colQuitData);
        colQuitPedido.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getIdPedido()));
        colQuitTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        MoneyCells.format(colQuitTotal);

        configurarItens(colItemQuitProduto, colItemQuitQtd, colItemQuitPreco, colItemQuitSubtotal);
        tableQuitados.getSelectionModel().selectedItemProperty().addListener((obs, antigo, pedido) ->
                mostrarItens(pedido, tableItensQuitado, labelItensQuitado));
    }

    private void configurarRecebimento() {
        btnFormaDinheiro.setUserData(PaymentMethod.DINHEIRO);
        btnFormaPix.setUserData(PaymentMethod.PIX);
        btnFormaCartao.setUserData(PaymentMethod.CARTAO);
        // Sempre há uma forma escolhida: clicar na já selecionada não a desmarca
        grupoForma.selectedToggleProperty().addListener((obs, antiga, nova) -> {
            if (nova == null && antiga != null) {
                antiga.setSelected(true);
            }
        });

        Constraints.setTextFieldMoney(campoValorPagamento);
        campoValorPagamento.setOnAction(e -> onPagarAction());
    }


    public void setCustomerData(Customer customer, OrderService service) {
        this.customer = customer;
        this.orderService = service;
        labelTitulo.setText("Conta de fiado: " + customer.getNomeCliente());
        carregarDados();
    }

    private void carregarDados() {
        if (orderService == null || customer == null) return;
        int id = customer.getIdCliente();

        List<Order> abertas = orderService.findOpenFiadoOrders(id);
        tableAbertos.setItems(FXCollections.observableArrayList(abertas));
        // Recalcula o atraso com os dados de agora (muda logo depois de um pagamento)
        customer.setValorEmAtraso(RegraFiado.valorEmAtraso(abertas, LocalDateTime.now()));
        idCaixaAberto = new CashRegisterService().findOpen().map(c -> c.getIdCaixa()).orElse(null);
        tableRecebimentos.setItems(FXCollections.observableArrayList(orderService.findFiadoReceipts(id)));
        btnEstornar.setDisable(true);
        tableQuitados.setItems(FXCollections.observableArrayList(orderService.findPaidOrdersByCustomer(id)));
        mostrarItens(null, tableItensAberto, labelItensAberto);
        mostrarItens(null, tableItensQuitado, labelItensQuitado);

        dividaAtual = orderService.getCustomerDebt(id);
        labelTotalDevendo.setText(Money.format(dividaAtual));

        BigDecimal disponivel = Money.max(Money.ZERO, customer.getLimiteCredito().subtract(dividaAtual));
        String situacao;
        if (customer.isBloqueadoPorAtraso()) {
            situacao = "EM ATRASO: pague " + Money.format(customer.getValorEmAtraso()) + " para liberar o fiado";
        } else {
            situacao = customer.getSituacaoFiado() == CustomerStatus.BLOQUEADO ? "Bloqueado" : "Liberado";
        }
        labelSituacao.setText(String.format("Situação: %s   |   Limite: %s   |   Disponível para fiado: %s",
                situacao, Money.format(customer.getLimiteCredito()), Money.format(disponivel)));

        btnPagar.setDisable(!Money.isPositive(dividaAtual));
    }

    private void mostrarItens(Order pedido, TableView<OrderItem> tabela, Label titulo) {
        if (pedido == null) {
            tabela.getItems().clear();
            titulo.setText("Selecione uma compra para ver os produtos.");
            return;
        }
        // Compras quitadas já vêm com os itens; as em aberto são buscadas ao selecionar
        List<OrderItem> itens = pedido.getItemPedido() != null
                ? pedido.getItemPedido()
                : orderService.findItemsByOrderId(pedido.getIdPedido());
        tabela.setItems(FXCollections.observableArrayList(itens));
        titulo.setText(String.format("Produtos da compra #%d de %s", pedido.getIdPedido(), DATA_HORA.format(pedido.getDataPedido())));
    }

    @FXML
    public void onPagarAction() {
        if (recebendo) return;
        if (customer == null || !Money.isPositive(dividaAtual)) {
            Alerts.showAlert("Aviso", null, "Este cliente não tem dívida de fiado.", Alert.AlertType.WARNING);
            return;
        }

        Toggle formaSelecionada = grupoForma.getSelectedToggle();
        if (formaSelecionada == null) {
            Alerts.showAlert("Aviso", null, "Escolha a forma de pagamento: Dinheiro, PIX ou Cartão.", Alert.AlertType.WARNING);
            return;
        }
        PaymentMethod forma = (PaymentMethod) formaSelecionada.getUserData();

        BigDecimal valorPago;
        String input = campoValorPagamento.getText();
        if (input == null || input.trim().isEmpty()) {
            valorPago = dividaAtual;
        } else {
            try {
                valorPago = Money.parse(input);
            } catch (NumberFormatException e) {
                Alerts.showAlert("Valor inválido", null, "Digite o valor recebido, por exemplo 25,00.", Alert.AlertType.WARNING);
                return;
            }
        }

        if (!Money.isPositive(valorPago)) {
            Alerts.showAlert("Valor inválido", null, "O valor recebido precisa ser maior que zero.", Alert.AlertType.WARNING);
            return;
        }
        if (valorPago.compareTo(dividaAtual) > 0) {
            Alerts.showAlert("Valor maior que a dívida", null,
                    String.format("O cliente deve %s. Receba no máximo esse valor.", Money.format(dividaAtual)),
                    Alert.AlertType.WARNING);
            return;
        }

        recebendo = true;
        btnPagar.setDisable(true);
        try {
            boolean confirmou = Alerts.showConfirmation(
                    "Confirmar recebimento",
                    valorPago.compareTo(dividaAtual) == 0 ? "Quitar toda a dívida" : "Recebimento parcial",
                    String.format("Receber %s em %s de %s?%n%nDívida depois do pagamento: %s",
                            Money.format(valorPago), nomeDaForma(forma), customer.getNomeCliente(),
                            Money.format(dividaAtual.subtract(valorPago))));
            if (!confirmou) return;
            boolean estavaEmAtraso = customer.isBloqueadoPorAtraso();
            RecebimentoFiado recebimento;
            try {
                recebimento = orderService.registerPartialPayment(customer.getIdCliente(), valorPago, forma);
            } catch (DbException | IllegalArgumentException e) {
                Alerts.showAlert("Erro", "Erro ao registrar o pagamento", e.getMessage(), Alert.AlertType.ERROR);
                return;
            }

            // Recebimento confirmado: nem a impressão nem a atualização da tela podem pedir novo pagamento.
            campoValorPagamento.clear();
            dividaAtual = recebimento.getDividaDepois();
            customer.setTotalDevendo(dividaAtual);
            labelTotalDevendo.setText(Money.format(dividaAtual));
            emitirComprovante(recebimento);
            boolean atualizado = false;
            try {
                carregarDados();
                atualizado = true;
            } catch (DbException e) {
                log.error("Recebimento #{} salvo, mas a conta não pôde ser atualizada na tela", recebimento.getIdRecebimento(), e);
                Alerts.showAlert("Pagamento registrado", "O pagamento foi salvo normalmente",
                        "Não foi possível atualizar a lista de compras. Reabra a conta para conferir. Não repita o pagamento.",
                        Alert.AlertType.WARNING);
            }
            if (atualizado && estavaEmAtraso && !customer.isBloqueadoPorAtraso()) {
                log.info("Fiado de {} liberado: compras atrasadas quitadas", customer.getNomeCliente());
                Alerts.showAlertAndWait("Fiado liberado", null,
                        "As compras atrasadas foram pagas. O fiado de " + customer.getNomeCliente() + " está liberado.",
                        Alert.AlertType.INFORMATION);
            }
            Alerts.showAlert("Pagamento registrado", null,
                    recebimento.getDividaDepois().signum() == 0 ? "Dívida quitada."
                            : "Pagamento registrado. Ainda falta " + Money.format(recebimento.getDividaDepois()) + ".",
                    Alert.AlertType.INFORMATION);
        } finally {
            recebendo = false;
            btnPagar.setDisable(!Money.isPositive(dividaAtual));
        }
    }

    private void emitirComprovante(RecebimentoFiado recebimento) {
        try {
            impressaoVendaService.emitir(recebimento).whenComplete((ignorado, erro) -> {
                if (erro != null) avisarFalhaComprovante(recebimento.getIdRecebimento(), erro);
            });
        } catch (Exception erro) {
            avisarFalhaComprovante(recebimento.getIdRecebimento(), erro);
        }
    }

    private void avisarFalhaComprovante(int idRecebimento, Throwable erro) {
        log.error("Recebimento #{} salvo, mas o comprovante não pôde ser enviado", idRecebimento, erro);
        javafx.application.Platform.runLater(() -> Alerts.showAlert("Comprovante não enviado",
                "O pagamento de fiado #" + idRecebimento + " foi salvo normalmente",
                "Não foi possível emitir o comprovante. Confira a impressora e o papel. Não repita o pagamento.",
                Alert.AlertType.WARNING));
    }

    private static String nomeDaForma(PaymentMethod forma) {
        if (forma == null) return "";
        switch (forma) {
            case DINHEIRO: return "Dinheiro";
            case PIX: return "PIX";
            case CARTAO: return "Cartão";
            default: return forma.toString();
        }
    }

    private void configurarItens(TableColumn<OrderItem, String> produto, TableColumn<OrderItem, Integer> qtd,
                                 TableColumn<OrderItem, BigDecimal> preco, TableColumn<OrderItem, BigDecimal> subtotal) {
        produto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNomeProduto()));
        qtd.setCellValueFactory(new PropertyValueFactory<>("qtd"));
        preco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        subtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));
        MoneyCells.format(preco);
        MoneyCells.format(subtotal);
        qtd.setStyle("-fx-alignment: CENTER;");
    }

    private static <S> void formatarDataHora(TableColumn<S, LocalDateTime> coluna) {
        coluna.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DATA_HORA.format(item));
            }
        });
    }

    /** Desfaz um recebimento lançado errado: o dinheiro volta ao cliente e as compras voltam a dever. */
    @FXML
    public void onEstornarAction() {
        Payment rec = tableRecebimentos.getSelectionModel().getSelectedItem();
        if (rec == null) return;
        javafx.stage.Window janela = tableRecebimentos.getScene().getWindow();
        if (!PinDialogo.liberar(acesso, janela, "Estornar recebimento", "estornar este recebimento")) return;

        String quando = rec.getDataPagamento().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        Optional<String> motivo = Alerts.pedirTexto("Estornar recebimento",
                "Estornar " + Money.format(rec.getValorPago()) + " (" + nomeDaForma(rec.getMetodo()) + ", " + quando + ")?",
                "As compras " + rec.getPedidos() + " voltam a dever esse valor.\n"
                        + "Devolva ao cliente " + Money.format(rec.getValorPago()) + " " + RegraTroca.comoDevolver(rec.getMetodo()) + ".\n\n"
                        + "Por que está estornando?",
                "Motivo (ex.: valor digitado errado)", "Estornar");
        if (motivo.isEmpty()) return;

        try {
            BigDecimal valor = orderService.estornarRecebimento(customer.getIdCliente(), rec.getDataPagamento(), rec.getMetodo(), motivo.get());
            log.warn("Recebimento de fiado estornado: {} {} de {}. Motivo: {}", Money.format(valor), rec.getMetodo(),
                    customer.getNomeCliente(), motivo.get());
            Alerts.showAlertAndWait("Recebimento estornado", null,
                    "Devolva ao cliente " + Money.format(valor) + " " + RegraTroca.comoDevolver(rec.getMetodo()) + ".",
                    Alert.AlertType.INFORMATION);
            carregarDados();
        } catch (RuntimeException e) {
            Alerts.showAlert("Estornar recebimento", "Não foi possível estornar", e.getMessage(), Alert.AlertType.WARNING);
        }
    }
}
