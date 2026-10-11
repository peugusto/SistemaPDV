package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Tema;
import com.casarural.sistemapdv.util.MoneyCells;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ProductListController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ProductListController.class);


    private ProductService service;

    @FXML private TextField searchField;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String> barcodeColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, BigDecimal> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private TableColumn<Product, Boolean> fiadoColumn;

    /** A partir deste número (inclusive) o estoque aparece em vermelho. */
    private static final int ESTOQUE_BAIXO = 15;
    @FXML private TableColumn<Product, Void> actionsColumn;

    private ObservableList<Product> obsList;


    public void setProductService(ProductService service) {
        this.service = service;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {

        idColumn.setCellValueFactory(new PropertyValueFactory<>("idProduto"));
        barcodeColumn.setCellValueFactory(new PropertyValueFactory<>("codBarras"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("nomeProduto"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("precoProduto"));
        MoneyCells.format(priceColumn);
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("estoque"));
        // Vermelho só na célula do produto com estoque baixo (antes a coluna inteira ficava vermelha)
        stockColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer estoque, boolean empty) {
                super.updateItem(estoque, empty);
                Product produto = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || estoque == null || produto == null) {
                    setText(null);
                    Tema.estado(this, null);
                } else if (!produto.isControlaEstoque()) {
                    // Pesável / sem controle: o número não significa nada, então não aparece
                    setText(produto.isPesavel() ? "Pesável" : "Não controla");
                    Tema.estado(this, "texto-suave");
                } else {
                    setText(String.valueOf(estoque));
                    Tema.estado(this, estoque <= ESTOQUE_BAIXO ? "texto-perigo" : null);
                }
            }
        });
        fiadoColumn.setCellValueFactory(new PropertyValueFactory<>("permiteFiado"));
        fiadoColumn.setCellFactory(col -> new TableCell<>() {
            private final Label etiqueta = new Label("Sem fiado");
            { etiqueta.getStyleClass().addAll("etiqueta", "etiqueta-perigo"); }
            @Override
            protected void updateItem(Boolean permite, boolean empty) {
                super.updateItem(permite, empty);
                setGraphic(null);
                setText(null);
                if (empty || permite == null) return;
                if (permite) {
                    setText("Sim");
                } else {
                    setGraphic(etiqueta);
                }
            }
        });

    }

    public void updateTableView() {
        if (service == null) {
            throw new IllegalStateException("Service was null");
        }
        List<Product> list = service.findAll();
        obsList = FXCollections.observableArrayList(list);


        FilteredList<Product> filteredData = new FilteredList<>(obsList, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(product -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();

                if (product.getNomeProduto().toLowerCase().contains(lowerCaseFilter)) return true;
                if (product.getCodBarras().contains(lowerCaseFilter)) return true;

                return false;
            });
        });

        productTable.setItems(filteredData);
        initEditButtons();
    }

    private void initEditButtons() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");
            private final HBox pane = new HBox(10, btnEdit, btnDelete);

            {
                pane.setAlignment(Pos.CENTER_LEFT);

                btnEdit.setOnAction(event -> {
                    Product obj = getTableView().getItems().get(getIndex());
                    onEditAction(obj);
                });

                btnDelete.setOnAction(event -> {
                    Product obj = getTableView().getItems().get(getIndex());
                    onDeleteAction(obj);
                });

                btnEdit.getStyleClass().setAll("button", "botao-pequeno", "botao-info");
                btnDelete.getStyleClass().setAll("button", "botao-pequeno", "botao-perigo");

            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });
    }

    private void onEditAction(Product obj) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/ProductForm.fxml"));
            javafx.scene.Parent parent = loader.load();

            ProductFormController controller = loader.getController();
            controller.setProduct(obj);
            controller.setServices(this.service, this);
            controller.updateFormData();

            Stage stage = new Stage();
            stage.setTitle("Editar Produto - " + obj.getNomeProduto());
            javafx.scene.Scene scene = new javafx.scene.Scene(parent);
            Tema.aplicar(scene);
            stage.setScene(scene);
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (java.io.IOException e) {
            log.error("Erro ao carregar o formulário de edição do produto {}", obj.getIdProduto(), e);
            Alerts.showAlert("Erro", "Erro ao carregar o formulário de edição", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void onDeleteAction(Product obj) {

        if(Alerts.showConfirmation("Confirmação","Deseja excluir o produto?",obj.getNomeProduto())){
            try{
                service.delete(obj.getIdProduto());
                updateTableView();
            }catch(DbException e){
                Alerts.showAlert("ERROR",null, e.getMessage(),Alert.AlertType.ERROR);
            }

        }

    }
}