package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.LocalDateTime;

public class OrderListController {

    @FXML private TableView<Order> tableViewItens;
    @FXML private TableColumn<OrderItem, LocalDateTime> columnDataHora;
    @FXML private TableColumn<OrderItem, Integer> columnVendaId;
    @FXML private TableColumn<OrderItem, String> columnProduto;
    @FXML private TableColumn<OrderItem, Double> columnPreco;
    @FXML private TableColumn<OrderItem, Integer> columnQtd;
    @FXML private TableColumn<OrderItem, Double> columnTotal;
    @FXML private TableColumn<OrderItem, Void> actionsColumn;

    private ObservableList<OrderItem> obsList;
}
