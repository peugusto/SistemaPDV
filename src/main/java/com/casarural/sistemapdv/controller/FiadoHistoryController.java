package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.services.OrderService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class FiadoHistoryController {

    @FXML
    private VBox vBoxPedidos;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void setHistoryData(Customer customer, OrderService service) {

        vBoxPedidos.getChildren().clear();


        List<Order> paidOrders = service.findPaidOrdersByCustomer(customer.getIdCliente());

        if (paidOrders.isEmpty()) {
            return;
        }

        for (Order order : paidOrders) {
            TitledPane pane = new TitledPane();


            String titulo = String.format("Pedido #%d | Data: %s | Total: R$ %.2f",
                    order.getIdPedido(),
                    order.getDataPedido().format(dtf),
                    order.getValorTotal());

            pane.setText(titulo);
            pane.setExpanded(false);

            TableView<OrderItem> table = createSimpleTable(order.getItemPedido());

            pane.setContent(table);
            vBoxPedidos.getChildren().add(pane);
        }
    }


    private TableView<OrderItem> createSimpleTable(List<OrderItem> items) {
        TableView<OrderItem> table = new TableView<>();
        table.setPrefHeight(200);


        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);


        TableColumn<OrderItem, String> colProd = new TableColumn<>("Produto");
        colProd.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getProduto().getNomeProduto()));
        colProd.setPrefWidth(200);


        TableColumn<OrderItem, Integer> colQtd = new TableColumn<>("Qtd");
        colQtd.setCellValueFactory(new PropertyValueFactory<>("qtd"));
        colQtd.setStyle("-fx-alignment: CENTER;");


        TableColumn<OrderItem, Double> colPreco = new TableColumn<>("Preço Unit.");
        colPreco.setCellValueFactory(new PropertyValueFactory<>("precoUnitario"));
        colPreco.setStyle("-fx-alignment: CENTER-RIGHT;");


        TableColumn<OrderItem, Double> colSub = new TableColumn<>("Subtotal");
        colSub.setCellValueFactory(cellData -> {
            double sub = cellData.getValue().getQtd() * cellData.getValue().getPrecoUnitario();
            return new SimpleObjectProperty<>(sub);
        });
        colSub.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.getColumns().addAll(colProd, colQtd, colPreco, colSub);


        if (items != null) {
            table.setItems(FXCollections.observableArrayList(items));
        }

        return table;
    }
}