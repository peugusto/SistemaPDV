package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class PDVController implements Initializable {

    private ProductService productService;
    private ObservableList<OrderItem> itensVenda = FXCollections.observableArrayList();


    @FXML private Label labelUltimoItem;
    @FXML private TextField campoCodigoProduto;
    @FXML private TextField campoQuantidade;
    @FXML private TextField campoValorUnitario;
    @FXML private TextField campoTotalItem;


    @FXML private ListView<OrderItem> listaItens;
    @FXML private Label labelTotal;


    @FXML private Button botaoAdicionar;
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

                    Label lDesc = new Label(item.getProduto().getNomeProduto());
                    lDesc.setPrefWidth(200);
                    lDesc.setStyle("-fx-wrap-text: true;");

                    Label lVal = new Label(String.format("%.2f", item.getPrecoUnitario()));
                    lVal.setPrefWidth(70);

                    Label lTot = new Label(String.format("%.2f", item.getSubtotal()));
                    lTot.setPrefWidth(70);

                    hBox.getChildren().addAll(lQtd, lDesc, lVal, lTot);
                    setGraphic(hBox);
                }
            }
        });


        botaoAdicionar.setOnAction(event -> onAdicionarItem());
        campoCodigoProduto.setOnAction(event -> onAdicionarItem()); // Permite adicionar apertando Enter no campo

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
                    if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        onLimparVenda();
                        event.consume();
                    }
                });
            }
        });
    }

    private void onAdicionarItem() {
        String codigo = campoCodigoProduto.getText();
        if (codigo == null || codigo.trim().isEmpty()) return;


        int qtdInput = 1;
        try {
            qtdInput = Integer.parseInt(campoQuantidade.getText().trim());
            if(qtdInput <= 0) qtdInput = 1;
        } catch (NumberFormatException e) {
            qtdInput = 1;
        }

        Optional<Product> prodOpt = productService.findByCodBarras(codigo);

        if (prodOpt.isPresent()) {
            Product p = prodOpt.get();
            final int quantidadeFinal = qtdInput;


            campoValorUnitario.setText(String.format("%.2f", p.getPrecoProduto()));
            campoTotalItem.setText(String.format("%.2f", p.getPrecoProduto() * quantidadeFinal));
            labelUltimoItem.setText(quantidadeFinal + " X " + p.getNomeProduto().toUpperCase());

            Optional<OrderItem> itemExistente = itensVenda.stream()
                    .filter(item -> item.getProduto().getCodBarras().equals(codigo))
                    .findFirst();

            if (itemExistente.isPresent()) {
                OrderItem item = itemExistente.get();
                item.setQtd(item.getQtd() + quantidadeFinal);
                listaItens.refresh(); // Atualiza o visual da lista
            } else {
                OrderItem novoItem = new OrderItem(p, quantidadeFinal, p.getPrecoProduto(), p.getPrecoProduto());
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
        if (itensVenda.isEmpty()) return;

        if (Alerts.showConfirmation("Cancelar Venda", null, "Deseja cancelar toda a venda atual?")) {
            itensVenda.clear();
            atualizarTotalVenda();
            resetarCamposVisuais();
        }
    }

    private void onFinalizarVenda() {
        if (itensVenda.isEmpty()) {
            Alerts.showAlert("Aviso", "Carrinho Vazio", "Adicione produtos antes de finalizar a venda.", Alert.AlertType.WARNING);
            return;
        }

        Order order = new Order();
        double total = itensVenda.stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();

        order.setValorTotal(total);
        order.setItemPedido(new java.util.ArrayList<>(itensVenda));
        order.setStatus(com.casarural.sistemapdv.model.entities.enums.OrderStatus.PAGO);

        com.casarural.sistemapdv.util.ViewLoader.showView(
                true,
                "/com/casarural/sistemapdv/view/FechamentoVenda.fxml",
                "Finalizar Pagamento - Casa Rural",
                (PaymentController controller) -> {
                    controller.setDadosVenda(
                            order,
                            new com.casarural.sistemapdv.services.OrderService(),
                            new com.casarural.sistemapdv.services.CustomerService(com.casarural.sistemapdv.model.dao.DaoFactory.createCustomerDAO())
                    );
                }
        );


        itensVenda.clear();
        atualizarTotalVenda();
        resetarCamposVisuais();
    }

    private void atualizarTotalVenda() {
        double total = itensVenda.stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();
        labelTotal.setText(String.format("R$ %.2f", total));
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