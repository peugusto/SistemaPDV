package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class PDVController implements Initializable {


    private ProductService productService;

    private ObservableList<OrderItem> itensVenda = FXCollections.observableArrayList();

    @FXML private TextField campoCodigoProduto;
    @FXML private TableView<OrderItem> tabelaItens;
    @FXML private TableColumn<OrderItem, String> colunaCodigo;
    @FXML private TableColumn<OrderItem, String> colunaProduto;
    @FXML private TableColumn<OrderItem, Integer> colunaQuantidade;
    @FXML private TableColumn<OrderItem, Double> colunaPreco;
    @FXML private TableColumn<OrderItem, Double> colunaTotal;

    @FXML private Label labelTotal;
    @FXML private Button botaoRemover;
    @FXML private Button botaoLimpar;

    public void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {

        colunaCodigo.setCellValueFactory(new PropertyValueFactory<>("codBarras"));
        colunaProduto.setCellValueFactory(new PropertyValueFactory<>("nomeProduto"));
        colunaQuantidade.setCellValueFactory(new PropertyValueFactory<>("qtd"));
        colunaPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        colunaTotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));

        tabelaItens.setItems(itensVenda);


        campoCodigoProduto.setOnAction(event -> onAdicionarItem());


        botaoRemover.setOnAction(event -> onRemoverItem());
        botaoLimpar.setOnAction(event -> onLimparVenda());
    }

    private void onAdicionarItem() {
        String codigo = campoCodigoProduto.getText();
        if (codigo == null || codigo.trim().isEmpty()) return;

        Optional<Product> prodOpt = productService.findByCodBarras(codigo);

        if (prodOpt.isPresent()) {
            Product p = prodOpt.get();


            Optional<OrderItem> itemExistente = itensVenda.stream()
                    .filter(item -> item.getProduto().getCodBarras().equals(codigo))
                    .findFirst();

            if (itemExistente.isPresent()) {

                OrderItem item = itemExistente.get();
                item.setQtd(item.getQtd() + 1);
                tabelaItens.refresh();
            } else {
                OrderItem novoItem = new OrderItem(p, 1, p.getPrecoProduto(), p.getPrecoProduto());
                itensVenda.add(novoItem);
            }

            atualizarTotalVenda();
            campoCodigoProduto.clear();
        } else {
            Alerts.showAlert("Aviso", null, "Produto não encontrado!", Alert.AlertType.WARNING);
            campoCodigoProduto.selectAll();
        }
    }

    private void onRemoverItem() {
        OrderItem selecionado = tabelaItens.getSelectionModel().getSelectedItem();
        if (selecionado != null) {
            itensVenda.remove(selecionado);
            atualizarTotalVenda();
        } else {
            Alerts.showAlert("Seleção", null, "Selecione um item para remover.", Alert.AlertType.INFORMATION);
        }
    }

    private void onLimparVenda() {
        if (Alerts.showConfirmation("Limpar", null, "Deseja cancelar toda a venda atual?")) {
            itensVenda.clear();
            atualizarTotalVenda();
            campoCodigoProduto.clear();
        }
    }

    private void atualizarTotalVenda() {
        double total = itensVenda.stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();
        labelTotal.setText(String.format("R$ %.2f", total));

    }
}