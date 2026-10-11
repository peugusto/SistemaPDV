package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.BuscaProduto;
import com.casarural.sistemapdv.services.BuscaProduto.Filtro;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Tema;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/** Busca de produto aberta pelo F2 do PDV. Devolve o produto escolhido, ou vazio se fechou. */
public class BuscaProdutoController implements Initializable {

    @FXML private TextField txtBusca;
    @FXML private ToggleGroup grupoFiltro;
    @FXML private ToggleButton btnTodos;
    @FXML private ToggleButton btnPesaveis;
    @FXML private ToggleButton btnSemControle;
    @FXML private ToggleButton btnCodigosCurtos;
    @FXML private CheckBox chkSoComEstoque;
    @FXML private TableView<Product> tabela;
    @FXML private TableColumn<Product, String> colCodigo;
    @FXML private TableColumn<Product, String> colNome;
    @FXML private TableColumn<Product, String> colPreco;
    @FXML private TableColumn<Product, String> colEstoque;
    @FXML private TableColumn<Product, Boolean> colFiado;
    @FXML private Label lblQuantidade;

    private FilteredList<Product> produtos;
    private Product escolhido;

    /** Abre a busca e espera a pessoa escolher ou fechar. */
    public static Optional<Product> abrir(ProductService service, Window dono) throws IOException {
        FXMLLoader loader = new FXMLLoader(BuscaProdutoController.class.getResource("/com/casarural/sistemapdv/view/BuscaProduto.fxml"));
        Parent root = loader.load();
        BuscaProdutoController controller = loader.getController();
        controller.setProdutos(service);

        Stage stage = new Stage();
        stage.setTitle("Buscar produto");
        if (dono != null) stage.initOwner(dono);
        stage.initModality(Modality.APPLICATION_MODAL);
        Scene scene = new Scene(root);
        Tema.aplicar(scene);
        controller.configurarTeclas(scene);
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(controller.escolhido);
    }

    /** Carrega todos os produtos uma vez; o filtro roda na memória a cada tecla. */
    public void setProdutos(ProductService service) {
        produtos = new FilteredList<>(FXCollections.observableArrayList(service.findAll()), p -> true);
        tabela.setItems(produtos);
        aplicarFiltro();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        btnTodos.setUserData(Filtro.TODOS);
        btnPesaveis.setUserData(Filtro.PESAVEIS);
        btnSemControle.setUserData(Filtro.SEM_ESTOQUE_CONTROLADO);
        btnCodigosCurtos.setUserData(Filtro.CODIGOS_CURTOS);

        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodBarras()));
        colNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNomeProduto()));
        colPreco.setCellValueFactory(c -> new SimpleStringProperty(
                Money.format(c.getValue().getPrecoProduto()) + (c.getValue().isPesavel() ? " /kg" : "")));
        colEstoque.setCellValueFactory(c -> new SimpleStringProperty(textoEstoque(c.getValue())));
        colEstoque.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean empty) {
                super.updateItem(texto, empty);
                Product p = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || p == null) {
                    setText(null);
                    Tema.estado(this, null);
                    return;
                }
                setText(texto);
                if (!p.isControlaEstoque()) Tema.estado(this, "texto-suave");
                else Tema.estado(this, p.getEstoque() <= 0 ? "texto-perigo" : null);
            }
        });
        colFiado.setCellValueFactory(c -> new javafx.beans.property.SimpleBooleanProperty(c.getValue().isPermiteFiado()).asObject());
        colFiado.setCellFactory(col -> new TableCell<>() {
            private final Label etiqueta = new Label("Sem fiado");
            { etiqueta.getStyleClass().addAll("etiqueta", "etiqueta-perigo"); }
            @Override
            protected void updateItem(Boolean permite, boolean empty) {
                super.updateItem(permite, empty);
                setText(null);
                setGraphic(null);
                if (empty || permite == null) return;
                if (permite) setText("Sim");
                else setGraphic(etiqueta);
            }
        });

        txtBusca.textProperty().addListener((obs, antes, agora) -> aplicarFiltro());
        grupoFiltro.selectedToggleProperty().addListener((obs, antes, agora) -> {
            // Pílula clicada de novo não fica sem nenhuma marcada
            if (agora == null) antes.setSelected(true);
            aplicarFiltro();
            txtBusca.requestFocus();
        });
        chkSoComEstoque.selectedProperty().addListener((obs, antes, agora) -> {
            aplicarFiltro();
            txtBusca.requestFocus();
        });

        tabela.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) escolher(row.getItem());
            });
            return row;
        });
    }

    private static String textoEstoque(Product p) {
        if (p.isPesavel()) return "Pesável";
        if (!p.isControlaEstoque()) return "Não controla";
        return String.valueOf(p.getEstoque());
    }

    private void configurarTeclas(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                onEscolher();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN && txtBusca.isFocused()) {
                // Desce da busca para a lista, já na segunda linha (a primeira já estava marcada)
                tabela.requestFocus();
                int i = Math.min(tabela.getSelectionModel().getSelectedIndex() + 1, produtos.size() - 1);
                tabela.getSelectionModel().select(Math.max(i, 0));
                tabela.scrollTo(Math.max(i - 3, 0));
                e.consume();
            } else if (e.getCode() == KeyCode.UP && tabela.isFocused() && tabela.getSelectionModel().getSelectedIndex() <= 0) {
                txtBusca.requestFocus();
                txtBusca.end();
                e.consume();
            }
        });
    }

    private void aplicarFiltro() {
        if (produtos == null) return;
        Toggle marcado = grupoFiltro.getSelectedToggle();
        Filtro filtro = marcado == null ? Filtro.TODOS : (Filtro) marcado.getUserData();
        String busca = txtBusca.getText();
        boolean soComEstoque = chkSoComEstoque.isSelected();
        produtos.setPredicate(p -> BuscaProduto.combina(p, busca, filtro, soComEstoque));

        // Primeira linha já marcada: digitou, apertou Enter, adicionou
        if (!produtos.isEmpty()) {
            tabela.getSelectionModel().selectFirst();
            tabela.scrollTo(0);
        }
        int total = produtos.getSource().size();
        lblQuantidade.setText(produtos.size() == total
                ? total + " produtos"
                : produtos.size() + " de " + total + " produtos");
    }

    @FXML
    public void onEscolher() {
        Product p = tabela.getSelectionModel().getSelectedItem();
        if (p != null) escolher(p);
    }

    private void escolher(Product p) {
        escolhido = p;
        fechar();
    }

    @FXML
    public void onFechar() {
        fechar();
    }

    private void fechar() {
        ((Stage) txtBusca.getScene().getWindow()).close();
    }
}
