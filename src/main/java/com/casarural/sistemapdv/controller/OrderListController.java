package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.SaleFilter;
import com.casarural.sistemapdv.model.entities.SaleSummary;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.MoneyCells;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;


/**
 * Histórico de vendas com filtros. Mostra o valor de cada venda (para conferir uma venda),
 * mas não soma valores do período: o faturamento fica para uma tela protegida.
 */
public class OrderListController implements Initializable {

    private static final int LIMITE = 500;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @FXML private ToggleGroup grupoPeriodo;
    @FXML private ToggleButton btnHoje;
    @FXML private ToggleButton btnOntem;
    @FXML private ToggleButton btn7Dias;
    @FXML private ToggleButton btnMes;
    @FXML private DatePicker dpInicio;
    @FXML private DatePicker dpFim;
    @FXML private ComboBox<PaymentMethod> comboForma;
    @FXML private ComboBox<OrderStatus> comboSituacao;
    @FXML private TextField campoCliente;
    @FXML private TextField campoProduto;

    @FXML private TableView<SaleSummary> tabelaVendas;
    @FXML private TableColumn<SaleSummary, LocalDateTime> colData;
    @FXML private TableColumn<SaleSummary, String> colNumero;
    @FXML private TableColumn<SaleSummary, String> colCliente;
    @FXML private TableColumn<SaleSummary, Integer> colItens;
    @FXML private TableColumn<SaleSummary, String> colFormas;
    @FXML private TableColumn<SaleSummary, BigDecimal> colValor;
    @FXML private TableColumn<SaleSummary, OrderStatus> colSituacao;

    @FXML private Label labelVendaTitulo;
    @FXML private Label labelVendaInfo;
    @FXML private TableView<OrderItem> tabelaItens;
    @FXML private TableColumn<OrderItem, String> colItemProduto;
    @FXML private TableColumn<OrderItem, Integer> colItemQtd;
    @FXML private TableColumn<OrderItem, BigDecimal> colItemPreco;
    @FXML private TableColumn<OrderItem, BigDecimal> colItemSubtotal;
    @FXML private ListView<Payment> listaPagamentos;
    @FXML private Label labelContagem;

    private final OrderService service = new OrderService();
    /** Evita buscar várias vezes enquanto o código ajusta datas e botões ao mesmo tempo. */
    private boolean ajustandoPeriodo = false;
    /** Espera a pessoa parar de digitar antes de buscar. */
    private final PauseTransition esperaDigitacao = new PauseTransition(Duration.millis(350));

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarFiltros();
        configurarTabelaVendas();
        configurarDetalhe();
        aplicarPeriodo(btnHoje);
    }

    // ===================== Filtros =====================

    private void configurarFiltros() {
        for (Toggle t : grupoPeriodo.getToggles()) {
            estilizarBotaoPeriodo((ToggleButton) t);
        }
        grupoPeriodo.selectedToggleProperty().addListener((obs, antigo, novo) -> {
            grupoPeriodo.getToggles().forEach(t -> estilizarBotaoPeriodo((ToggleButton) t));
            if (novo != null && !ajustandoPeriodo) aplicarPeriodo((ToggleButton) novo);
        });

        // Datas digitadas à mão: desmarca o período rápido e busca
        dpInicio.valueProperty().addListener((o, a, n) -> datasAlteradas());
        dpFim.valueProperty().addListener((o, a, n) -> datasAlteradas());
        dpInicio.setConverter(conversorData());
        dpFim.setConverter(conversorData());

        comboForma.setItems(FXCollections.observableArrayList(
                null, PaymentMethod.DINHEIRO, PaymentMethod.PIX, PaymentMethod.CARTAO, PaymentMethod.FIADO));
        comboForma.setConverter(new StringConverter<>() {
            @Override public String toString(PaymentMethod m) { return m == null ? "Todas as formas" : SaleSummary.nomeDaForma(m); }
            @Override public PaymentMethod fromString(String s) { return null; }
        });
        comboForma.setButtonCell(celulaCombo(comboForma.getConverter()));
        comboForma.setValue(null);
        comboForma.valueProperty().addListener((o, a, n) -> buscar());

        comboSituacao.setItems(FXCollections.observableArrayList(null, OrderStatus.PAGO, OrderStatus.FIADO));
        comboSituacao.setConverter(new StringConverter<>() {
            @Override public String toString(OrderStatus s) { return s == null ? "Todas as situações" : nomeDaSituacao(s); }
            @Override public OrderStatus fromString(String s) { return null; }
        });
        comboSituacao.setButtonCell(celulaCombo(comboSituacao.getConverter()));
        comboSituacao.setValue(null);
        comboSituacao.valueProperty().addListener((o, a, n) -> buscar());

        esperaDigitacao.setOnFinished(e -> buscar());
        campoCliente.textProperty().addListener((o, a, n) -> esperaDigitacao.playFromStart());
        campoProduto.textProperty().addListener((o, a, n) -> esperaDigitacao.playFromStart());
    }

    private static <T> ListCell<T> celulaCombo(StringConverter<T> conv) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? conv.toString(null) : conv.toString(item));
            }
        };
    }

    private static StringConverter<LocalDate> conversorData() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : f.format(d); }
            @Override public LocalDate fromString(String s) {
                try { return s == null || s.isBlank() ? null : LocalDate.parse(s.trim(), f); }
                catch (Exception e) { return null; }
            }
        };
    }

    private void estilizarBotaoPeriodo(ToggleButton b) {
        b.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6; "
                + (b.isSelected() ? "-fx-background-color: #27ae60; -fx-text-fill: white;"
                                  : "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white;"));
    }

    private void aplicarPeriodo(ToggleButton botao) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje, fim = hoje;
        if (botao == btnOntem) {
            inicio = fim = hoje.minusDays(1);
        } else if (botao == btn7Dias) {
            inicio = hoje.minusDays(6);
        } else if (botao == btnMes) {
            inicio = hoje.withDayOfMonth(1);
        }
        ajustandoPeriodo = true;
        botao.setSelected(true);
        dpInicio.setValue(inicio);
        dpFim.setValue(fim);
        ajustandoPeriodo = false;
        buscar();
    }

    private void datasAlteradas() {
        if (ajustandoPeriodo) return;
        ajustandoPeriodo = true;
        grupoPeriodo.selectToggle(null);
        ajustandoPeriodo = false;
        buscar();
    }

    @FXML
    public void onLimparFiltros() {
        ajustandoPeriodo = true;
        comboForma.setValue(null);
        comboSituacao.setValue(null);
        campoCliente.clear();
        campoProduto.clear();
        esperaDigitacao.stop();
        ajustandoPeriodo = false;
        aplicarPeriodo(btnHoje);
    }

    // ===================== Busca =====================

    private void buscar() {
        if (ajustandoPeriodo) return;
        LocalDate inicio = dpInicio.getValue();
        LocalDate fim = dpFim.getValue();
        if (inicio == null || fim == null) return;
        if (fim.isBefore(inicio)) {
            labelContagem.setText("A data final é anterior à inicial.");
            tabelaVendas.getItems().clear();
            return;
        }

        SaleFilter filtro = new SaleFilter(inicio, fim)
                .setForma(comboForma.getValue())
                .setStatus(comboSituacao.getValue())
                .setCliente(campoCliente.getText())
                .setProduto(campoProduto.getText());
        try {
            List<SaleSummary> vendas = service.findSales(filtro, LIMITE);
            tabelaVendas.setItems(FXCollections.observableArrayList(vendas));
            mostrarDetalhe(null);
            if (vendas.isEmpty()) {
                labelContagem.setText("Nenhuma venda encontrada.");
            } else if (vendas.size() == LIMITE) {
                labelContagem.setText("Mostrando as " + LIMITE + " vendas mais recentes. Use os filtros para encontrar vendas mais antigas.");
            } else {
                labelContagem.setText(vendas.size() == 1 ? "1 venda encontrada" : vendas.size() + " vendas encontradas");
            }
        } catch (DbException e) {
            Alerts.showAlert("Histórico de vendas", null, e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    // ===================== Tabela de vendas =====================

    private void configurarTabelaVendas() {
        colData.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDataPedido()));
        colData.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LocalDateTime d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : DATA_HORA.format(d));
            }
        });
        colNumero.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getIdPedido()));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNomeCliente() == null ? "—" : c.getValue().getNomeCliente()));
        colItens.setCellValueFactory(new PropertyValueFactory<>("quantidadeItens"));
        colItens.setStyle("-fx-alignment: CENTER;");
        colFormas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormasTexto()));
        colValor.setCellValueFactory(new PropertyValueFactory<>("valorTotal"));
        MoneyCells.format(colValor);
        colValor.setStyle("-fx-alignment: CENTER-RIGHT;");
        colSituacao.setCellValueFactory(new PropertyValueFactory<>("status"));
        colSituacao.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(OrderStatus s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(nomeDaSituacao(s));
                    setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: "
                            + (s == OrderStatus.FIADO ? "#d35400;" : "#27ae60;"));
                }
            }
        });
        tabelaVendas.getSelectionModel().selectedItemProperty().addListener((o, a, venda) -> mostrarDetalhe(venda));
    }

    private static String nomeDaSituacao(OrderStatus s) {
        switch (s) {
            case PAGO: return "Paga";
            case FIADO: return "A receber";
            default: return s.toString();
        }
    }

    // ===================== Detalhe =====================

    private void configurarDetalhe() {
        colItemProduto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNomeProduto()));
        colItemQtd.setCellValueFactory(new PropertyValueFactory<>("qtd"));
        colItemQtd.setStyle("-fx-alignment: CENTER;");
        colItemPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        colItemSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));
        MoneyCells.format(colItemPreco);
        MoneyCells.format(colItemSubtotal);

        // Pagamento em colunas alinhadas: forma | valor | quando
        listaPagamentos.setCellFactory(lv -> new ListCell<>() {
            private final Label forma = new Label();
            private final Label valor = new Label();
            private final Label quando = new Label();
            private final HBox linha = new HBox(12, forma, valor, quando);
            {
                forma.setMinWidth(70);
                forma.setStyle("-fx-font-weight: bold;");
                valor.setMinWidth(85);
                valor.setAlignment(Pos.CENTER_RIGHT);
                quando.setStyle("-fx-text-fill: #7f8c8d;");
                linha.setAlignment(Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(Payment p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }
                forma.setText(SaleSummary.nomeDaForma(p.getMetodo()));
                valor.setText(Money.format(p.getValorPago()));
                quando.setText(descreverQuando(p));
                setGraphic(linha);
            }
        });
    }

    private void mostrarDetalhe(SaleSummary venda) {
        if (venda == null) {
            labelVendaTitulo.setText("Selecione uma venda");
            labelVendaInfo.setText("Os produtos e os pagamentos da venda aparecem aqui.");
            tabelaItens.getItems().clear();
            listaPagamentos.getItems().clear();
            return;
        }
        labelVendaTitulo.setText("Venda #" + venda.getIdPedido() + "  ·  " + Money.format(venda.getValorTotal()));
        labelVendaInfo.setText(DATA_HORA.format(venda.getDataPedido())
                + (venda.getNomeCliente() != null ? "   ·   Cliente: " + venda.getNomeCliente() : "")
                + "   ·   " + nomeDaSituacao(venda.getStatus()));
        try {
            tabelaItens.setItems(FXCollections.observableArrayList(service.findItemsByOrderId(venda.getIdPedido())));
            listaPagamentos.setItems(FXCollections.observableArrayList(service.findPaymentsByOrder(venda.getIdPedido())));
        } catch (DbException e) {
            Alerts.showAlert("Histórico de vendas", null, e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    private static String descreverQuando(Payment p) {
        if (p.getOrigem() == PaymentOrigin.RECEBIMENTO_FIADO) {
            return "recebido depois, em " + DATA_CURTA.format(p.getDataPagamento());
        }
        return p.getMetodo() == PaymentMethod.FIADO ? "ficou na conta do cliente" : "na hora da venda";
    }
}
