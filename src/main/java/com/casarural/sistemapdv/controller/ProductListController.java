package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class ProductListController implements Initializable {


    private ProductService service;

    @FXML private TextField searchField;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String> barcodeColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
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
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("estoque"));

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
            private final Button editBtn = new Button("Editar");
            private final Button deleteBtn = new Button("Excluir");
            private final HBox pane = new HBox(10, editBtn, deleteBtn);

            {
                pane.setAlignment(Pos.CENTER);

                editBtn.setOnAction(event -> {
                    Product obj = getTableView().getItems().get(getIndex());
                    onEditAction(obj);
                });

                deleteBtn.setOnAction(event -> {
                    Product obj = getTableView().getItems().get(getIndex());
                    onDeleteAction(obj);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    private void onEditAction(Product obj) {
        System.out.println("Abrir formulário de edição para: " + obj.getNomeProduto());

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