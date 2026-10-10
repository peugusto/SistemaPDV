package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.MoneyCells;
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
import java.util.ResourceBundle;

/**
 * Conta de fiado de um cliente: compras em aberto (com o que já foi pago de cada uma),
 * pagamentos recebidos, compras quitadas e o recebimento de novos pagamentos.
 */
public class FiadoListController implements Initializable {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label labelTitulo;
    @FXML private Label labelSituacao;
    @FXML private Label labelTotalDevendo;

    // Compras em aberto
    @FXML private TableView<Order> tableAbertos;
    @FXML private TableColumn<Order, LocalDateTime> colAbertoData;
    @FXML private TableColumn<Order, String> colAbertoPedido;
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
        colAbertoTotal.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        colAbertoPagoVenda.setCellValueFactory(new PropertyValueFactory<>("pagoNaVenda"));
        colAbertoPagoDepois.setCellValueFactory(new PropertyValueFactory<>("recebidoDepois"));
        colAbertoFalta.setCellValueFactory(new PropertyValueFactory<>("falta"));
        MoneyCells.format(colAbertoTotal);
        MoneyCells.format(colAbertoPagoVenda);
        MoneyCells.format(colAbertoPagoDepois);
        MoneyCells.format(colAbertoFalta);
        colAbertoFalta.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");

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
            atualizarEstiloFormas();
        });
        atualizarEstiloFormas();

        Constraints.setTextFieldMoney(campoValorPagamento);
        campoValorPagamento.setOnAction(e -> onPagarAction());
    }

    private void atualizarEstiloFormas() {
        String base = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6; ";
        for (Toggle t : grupoForma.getToggles()) {
            ToggleButton b = (ToggleButton) t;
            b.setStyle(base + (b.isSelected()
                    ? "-fx-background-color: #27ae60; -fx-text-fill: white;"
                    : "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white;"));
        }
    }

    public void setCustomerData(Customer customer, OrderService service) {
        this.customer = customer;
        this.orderService = service;
        labelTitulo.setText("CONTA DE FIADO: " + customer.getNomeCliente().toUpperCase());
        carregarDados();
    }

    private void carregarDados() {
        if (orderService == null || customer == null) return;
        int id = customer.getIdCliente();

        tableAbertos.setItems(FXCollections.observableArrayList(orderService.findOpenFiadoOrders(id)));
        tableRecebimentos.setItems(FXCollections.observableArrayList(orderService.findFiadoReceipts(id)));
        tableQuitados.setItems(FXCollections.observableArrayList(orderService.findPaidOrdersByCustomer(id)));
        mostrarItens(null, tableItensAberto, labelItensAberto);
        mostrarItens(null, tableItensQuitado, labelItensQuitado);

        dividaAtual = orderService.getCustomerDebt(id);
        labelTotalDevendo.setText(Money.format(dividaAtual));

        BigDecimal disponivel = Money.max(Money.ZERO, customer.getLimiteCredito().subtract(dividaAtual));
        labelSituacao.setText(String.format("Situação: %s   |   Limite: %s   |   Disponível para fiado: %s",
                customer.getSituacaoFiado(), Money.format(customer.getLimiteCredito()), Money.format(disponivel)));

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

        boolean quita = valorPago.compareTo(dividaAtual) == 0;
        boolean confirmou = Alerts.showConfirmation(
                "Confirmar recebimento",
                quita ? "Quitar toda a dívida" : "Recebimento parcial",
                String.format("Receber %s em %s de %s?%n%nDívida depois do pagamento: %s",
                        Money.format(valorPago), nomeDaForma(forma), customer.getNomeCliente(),
                        Money.format(dividaAtual.subtract(valorPago))));
        if (!confirmou) return;

        try {
            orderService.registerPartialPayment(customer.getIdCliente(), valorPago, forma);
            campoValorPagamento.clear();
            carregarDados();
            Alerts.showAlert("Pagamento registrado", null,
                    quita ? "Dívida quitada." : "Pagamento registrado. Ainda falta " + Money.format(dividaAtual) + ".",
                    Alert.AlertType.INFORMATION);
        } catch (DbException e) {
            Alerts.showAlert("Erro", "Erro ao registrar o pagamento", e.getMessage(), Alert.AlertType.ERROR);
        }
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
}
