package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Money;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class PDVController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PDVController.class);

    private ProductService productService;
    private ObservableList<OrderItem> itensVenda = FXCollections.observableArrayList();
    /** Troca em andamento: a venda que vai ser substituída (null = venda normal). */
    private VendaParaDesfazer trocaDe;
    /** Na troca, o estoque que a venda antiga devolve ao ser cancelada (id do produto -> quantidade). */
    private final Map<Integer, Integer> estoqueDaTroca = new HashMap<>();
    private Runnable aoConcluirTroca;


    @FXML private Label labelUltimoItem;
    @FXML private TextField campoCodigoProduto;
    @FXML private TextField campoQuantidade;
    @FXML private TextField campoValorUnitario;
    @FXML private TextField campoTotalItem;


    @FXML private ListView<OrderItem> listaItens;
    @FXML private Label labelTotal;


    @FXML private Button botaoAdicionar;
    @FXML private Button botaoBuscar;
    @FXML private Button botaoRemover;
    @FXML private Button botaoLimpar;
    @FXML private Button botaoFinalizar;

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {
        listaItens.setItems(itensVenda);
        listaItens.setCellFactory(param -> new ListCell<OrderItem>() {
            @Override
            protected void updateItem(OrderItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox hBox = new HBox(10);

                    Label lQtd = new Label(String.valueOf(item.getQtd()));
                    lQtd.setPrefWidth(40);

                    // Produto que não pode ir para o fiado aparece marcado no carrinho
                    Label lDesc = new Label(item.getProduto().getNomeProduto()
                            + (item.getProduto().isPesavel() ? "  (balança)" : "")
                            + (item.getProduto().isPermiteFiado() ? "" : "  [sem fiado]"));
                    lDesc.setPrefWidth(200);
                    lDesc.setStyle("-fx-wrap-text: true;");

                    Label lVal = new Label(Money.formatPlain(item.getPrecoUnitario()));
                    lVal.setPrefWidth(70);

                    Label lTot = new Label(Money.formatPlain(item.getSubtotal()));
                    lTot.setPrefWidth(70);

                    hBox.getChildren().addAll(lQtd, lDesc, lVal, lTot);
                    setGraphic(hBox);
                }
            }
        });


        botaoAdicionar.setOnAction(event -> onAdicionarItem());
        campoCodigoProduto.setOnAction(event -> onAdicionarItem()); // Permite adicionar apertando Enter no campo

        botaoBuscar.setOnAction(event -> onBuscarProduto());
        botaoRemover.setOnAction(event -> onRemoverItem());
        botaoLimpar.setOnAction(event -> onLimparVenda());
        botaoFinalizar.setOnAction(event -> onFinalizarVenda());


        labelTotal.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.F1) {
                        onFinalizarVenda();
                        event.consume();
                    }
                    if (event.getCode() == javafx.scene.input.KeyCode.F2) {
                        onBuscarProduto();
                        event.consume();
                    }
                    // Esc só é do PDV quando há o que cancelar; cupom vazio: a janela pergunta se fecha
                    if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && (trocaDe != null || !itensVenda.isEmpty())) {
                        onLimparVenda();
                        event.consume();
                    }
                });
            }
        });
    }

    private void onAdicionarItem() {
        String codigo = campoCodigoProduto.getText() == null ? "" : campoCodigoProduto.getText().trim();
        if (codigo.isEmpty()) return;


        int qtdInput = 1;
        try {
            qtdInput = Integer.parseInt(campoQuantidade.getText().trim());
            if(qtdInput <= 0) qtdInput = 1;
        } catch (NumberFormatException e) {
            qtdInput = 1;
        }

        Optional<Product> prodOpt = productService.findByCodBarras(codigo);

        if (prodOpt.isPresent() && !prodOpt.get().isAtivo()) {
            Alerts.showAlert("Produto desativado", prodOpt.get().getNomeProduto(),
                    "Este produto foi desativado e não pode ser vendido. Para vender de novo, marque "
                            + "\"Produto ativo\" em Estoque > Editar.", Alert.AlertType.WARNING);
            campoCodigoProduto.selectAll();
            return;
        }
        if (prodOpt.isPresent()) {
            Product p = prodOpt.get();

            // Pesável: vale o valor da balança, cada pesagem é uma linha própria
            if (p.isPesavel()) {
                adicionarPesavel(p);
                return;
            }

            final int quantidadeFinal = qtdInput;

            Optional<OrderItem> itemExistente = itensVenda.stream()
                    .filter(item -> item.getProduto().getCodBarras().equals(codigo))
                    .findFirst();

            int qtdNoCarrinho = itemExistente.map(OrderItem::getQtd).orElse(0);
            int devolveNaTroca = estoqueDaTroca.getOrDefault(p.getIdProduto(), 0);
            if (!p.temEstoquePara(qtdNoCarrinho + quantidadeFinal - devolveNaTroca)) {
                Alerts.showAlert("Estoque Insuficiente", null,
                        String.format("Estoque disponível de '%s': %d (já no carrinho: %d).",
                                p.getNomeProduto(), Math.max(0, p.getEstoque()), qtdNoCarrinho),
                        Alert.AlertType.WARNING);
                campoCodigoProduto.selectAll();
                return;
            }

            campoValorUnitario.setText(Money.formatPlain(p.getPrecoProduto()));
            campoTotalItem.setText(Money.formatPlain(p.getPrecoProduto().multiply(BigDecimal.valueOf(quantidadeFinal))));
            labelUltimoItem.setText(quantidadeFinal + " X " + p.getNomeProduto().toUpperCase());

            if (itemExistente.isPresent()) {
                OrderItem item = itemExistente.get();
                item.setQtd(item.getQtd() + quantidadeFinal);
                listaItens.refresh(); // Atualiza o visual da lista
            } else {
                OrderItem novoItem = new OrderItem(p, quantidadeFinal, p.getPrecoProduto());
                itensVenda.add(novoItem);
            }

            atualizarTotalVenda();


            campoCodigoProduto.clear();
            campoQuantidade.setText("1");
            campoCodigoProduto.requestFocus();


            listaItens.scrollTo(itensVenda.size() - 1);
        } else {
            Alerts.showAlert("Aviso", null, "Produto não encontrado!", Alert.AlertType.WARNING);
            campoCodigoProduto.selectAll();
        }
    }

    /**
     * Queijo, mortadela...: a balança mostra o preço e o caixa digita esse valor.
     * Entra como 1 item com o valor digitado; não mexe no estoque.
     */
    private void adicionarPesavel(Product p) {
        Optional<BigDecimal> valor = Alerts.pedirValor(
                "Produto pesado",
                p.getNomeProduto().toUpperCase() + "  —  " + Money.format(p.getPrecoProduto()) + " o kg",
                "Valor na balança (R$):");
        if (valor.isEmpty()) {
            campoCodigoProduto.selectAll();
            campoCodigoProduto.requestFocus();
            return;
        }

        itensVenda.add(new OrderItem(p, 1, valor.get()));
        campoValorUnitario.setText(Money.formatPlain(valor.get()));
        campoTotalItem.setText(Money.formatPlain(valor.get()));
        labelUltimoItem.setText(p.getNomeProduto().toUpperCase() + " (BALANÇA)");
        atualizarTotalVenda();

        campoCodigoProduto.clear();
        campoQuantidade.setText("1");
        campoCodigoProduto.requestFocus();
        listaItens.scrollTo(itensVenda.size() - 1);
    }

    /**
     * F2: procura pelo nome. O produto escolhido entra como se o código tivesse sido digitado,
     * então vale a quantidade do campo, a janela da balança e a conferência de estoque.
     */
    private void onBuscarProduto() {
        try {
            Optional<Product> escolhido = BuscaProdutoController.abrir(productService, labelTotal.getScene().getWindow());
            if (escolhido.isPresent()) {
                campoCodigoProduto.setText(escolhido.get().getCodBarras());
                onAdicionarItem();
            } else {
                campoCodigoProduto.requestFocus();
            }
        } catch (Exception e) {
            log.error("Erro ao abrir a busca de produto", e);
            Alerts.showAlert("Buscar produto", null, "Não foi possível abrir a busca de produto.", Alert.AlertType.ERROR);
        }
    }

    private void onRemoverItem() {
        OrderItem selecionado = listaItens.getSelectionModel().getSelectedItem();
        if (selecionado != null) {
            itensVenda.remove(selecionado);
            atualizarTotalVenda();
            labelUltimoItem.setText("ITEM REMOVIDO");
            campoValorUnitario.setText("0,00");
            campoTotalItem.setText("0,00");
            campoCodigoProduto.requestFocus();
        } else {
            Alerts.showAlert("Seleção", null, "Selecione um item na lista para remover.", Alert.AlertType.INFORMATION);
        }
    }

    private void onLimparVenda() {
        if (trocaDe != null) {
            if (Alerts.showConfirmation("Desistir da troca", null,
                    "Desistir da troca? A venda #" + trocaDe.getIdPedido() + " continua como estava.")) {
                fecharJanela();
            }
            return;
        }
        if (itensVenda.isEmpty()) return;

        if (Alerts.showConfirmation("Cancelar Venda", null, "Deseja cancelar toda a venda atual?")) {
            itensVenda.clear();
            atualizarTotalVenda();
            resetarCamposVisuais();
        }
    }

    /**
     * Começa uma troca: o cupom vem com os itens da venda antiga. O caixa tira o produto errado,
     * passa o certo e finaliza; o que o cliente já pagou entra como crédito no pagamento.
     */
    public void iniciarTroca(VendaParaDesfazer venda, Runnable aoConcluir) {
        this.trocaDe = venda;
        this.aoConcluirTroca = aoConcluir;
        estoqueDaTroca.clear();
        itensVenda.clear();
        for (OrderItem i : venda.getItens()) {
            itensVenda.add(new OrderItem(i.getProduto(), i.getQtd(), i.getPrecoUnitario()));
            estoqueDaTroca.merge(i.getProduto().getIdProduto(), i.getQtd(), Integer::sum);
        }
        atualizarTotalVenda();
        labelUltimoItem.setText("TROCA DA VENDA #" + venda.getIdPedido());
        campoCodigoProduto.requestFocus();
    }

    private void fecharJanela() {
        ((javafx.stage.Stage) labelTotal.getScene().getWindow()).close();
    }

    private void onFinalizarVenda() {
        if (itensVenda.isEmpty()) {
            Alerts.showAlert("Aviso", "Carrinho Vazio", "Adicione produtos antes de finalizar a venda.", Alert.AlertType.WARNING);
            return;
        }

        Order order = new Order();
        order.setItemPedido(new java.util.ArrayList<>(itensVenda));
        order.aplicarDesconto(java.math.BigDecimal.ZERO);   // total = soma dos itens
        if (trocaDe != null) order.setIdPedidoTrocado(trocaDe.getIdPedido());
        order.setStatus(com.casarural.sistemapdv.model.entities.enums.OrderStatus.PAGO);

        PaymentController[] pagamento = new PaymentController[1];
        com.casarural.sistemapdv.util.ViewLoader.showView(
                true,
                "/com/casarural/sistemapdv/view/FechamentoVenda.fxml",
                "Finalizar Pagamento - Casa Rural",
                (PaymentController controller) -> {
                    pagamento[0] = controller;
                    controller.setDadosVenda(
                            order,
                            new com.casarural.sistemapdv.services.OrderService(),
                            new com.casarural.sistemapdv.services.CustomerService(com.casarural.sistemapdv.model.dao.DaoFactory.createCustomerDAO())
                    );
                    if (trocaDe != null) controller.setTroca(trocaDe);
                }
        );

        // Resultado incerto não é cancelamento: reabrir este carrinho criaria outra venda.
        if (pagamento[0] != null && pagamento[0].isVendaPendenteConferencia()) {
            fecharJanela();
            return;
        }

        // Se o pagamento foi cancelado, mantém o carrinho para o operador continuar a venda
        if (pagamento[0] != null && pagamento[0].isVendaConcluida() && trocaDe != null) {
            // Troca feita: volta para o histórico
            if (aoConcluirTroca != null) aoConcluirTroca.run();
            fecharJanela();
        } else if (pagamento[0] != null && pagamento[0].isVendaConcluida()) {
            itensVenda.clear();
            atualizarTotalVenda();
            resetarCamposVisuais();
        } else {
            campoCodigoProduto.requestFocus();
        }
    }

    private BigDecimal calcularTotalVenda() {
        return itensVenda.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private void atualizarTotalVenda() {
        labelTotal.setText(Money.format(calcularTotalVenda()));
    }

    private void resetarCamposVisuais() {
        labelUltimoItem.setText("CAIXA LIVRE");
        campoCodigoProduto.clear();
        campoQuantidade.setText("1");
        campoValorUnitario.setText("0,00");
        campoTotalItem.setText("0,00");
        campoCodigoProduto.requestFocus();
    }
}
