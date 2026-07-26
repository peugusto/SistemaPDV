package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class CustomerListController implements Initializable {

    private CustomerService service;

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, CustomerStatus> creditStatusColumn;
    @FXML private TableColumn<Customer, Double> creditLimitColumn;
    @FXML private TableColumn<Customer, Double> totalDebtColumn;
    @FXML private TableColumn<Customer, Void> actionsColumn;

    @FXML private TextField campoPesquisa;
    @FXML private ComboBox<String> comboFiltroStatus;
    @FXML private ComboBox<String> comboFiltroOrdenacao;

    private ObservableList<Customer> obsList;
    private FilteredList<Customer> filteredData;

    public void setCustomerService(CustomerService service) {
        this.service = service;
        updateTableView();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initializeNodes();
    }

    private void initializeNodes() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("idCliente"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("nomeCliente"));
        creditStatusColumn.setCellValueFactory(new PropertyValueFactory<>("situacaoFiado"));
        creditLimitColumn.setCellValueFactory(new PropertyValueFactory<>("limiteCredito"));
        totalDebtColumn.setCellValueFactory(new PropertyValueFactory<>("totalDevendo"));

        idColumn.setStyle("-fx-alignment: CENTER;");
        nameColumn.setStyle("-fx-alignment: CENTER;");
        creditLimitColumn.setStyle("-fx-alignment: CENTER;");
        totalDebtColumn.setStyle("-fx-alignment: CENTER;");
        creditStatusColumn.setStyle("-fx-alignment: CENTER;");

        creditStatusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(CustomerStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status.toString());
                    if (status == CustomerStatus.BLOQUEADO) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                    }
                }
            }
        });

        totalDebtColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    double debt = (item != null) ? item : 0.0;
                    setText(String.format("R$ %.2f", debt));
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: " + (debt > 0 ? "#e74c3c" : "#2ecc71") + "; -fx-font-weight: bold;");
                }
            }
        });

        customerTable.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showFiadoDetails(row.getItem());
                }
            });
            return row;
        });

        initButtons();
        configurarFiltrosEEstatisticas();
    }

    private void configurarFiltrosEEstatisticas() {
        comboFiltroStatus.setItems(FXCollections.observableArrayList("Todos", "DISPONIVEL", "BLOQUEADO"));
        comboFiltroStatus.setValue("Todos");

        comboFiltroOrdenacao.setItems(FXCollections.observableArrayList("Padrão", "Maior Dívida", "Menor Dívida"));
        comboFiltroOrdenacao.setValue("Padrão");

        comboFiltroStatus.setOnAction(e -> aplicarFiltros());
        comboFiltroOrdenacao.setOnAction(e -> aplicarFiltros());

        if (campoPesquisa != null) {
            campoPesquisa.textProperty().addListener((observable, oldValue, newValue) -> aplicarFiltros());
        }
    }

    private void aplicarFiltros() {
        if (filteredData == null) return;

        filteredData.setPredicate(customer -> {
            String searchText = campoPesquisa.getText();
            boolean matchesSearch = (searchText == null || searchText.trim().isEmpty()) ||
                    customer.getNomeCliente().toLowerCase().contains(searchText.toLowerCase().trim());

            String statusFilter = comboFiltroStatus.getValue();
            boolean matchesStatus = statusFilter == null || statusFilter.equals("Todos") ||
                    customer.getSituacaoFiado().toString().equalsIgnoreCase(statusFilter);

            return matchesSearch && matchesStatus;
        });
    }

    private void showFiadoDetails(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/FiadoList.fxml"));
            Parent parent = loader.load();

            FiadoListController controller = loader.getController();
            controller.setCustomerData(customer, new OrderService());

            Stage stage = new Stage();
            stage.setTitle("Detalhamento de Fiado - " + customer.getNomeCliente());
            stage.setScene(new Scene(parent));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            updateTableView();
        } catch (Exception e) {
            Alerts.showAlert("Erro", "Erro ao abrir detalhes", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void updateTableView() {
        if (service == null) return;

        List<Customer> list = service.findAll();
        OrderService orderService = new OrderService();

        for (Customer c : list) {
            double debt = orderService.getCustomerDebt(c.getIdCliente());
            c.setTotalDevendo(debt);
        }

        obsList = FXCollections.observableArrayList(list);
        filteredData = new FilteredList<>(obsList, b -> true);

        SortedList<Customer> sortedData = new SortedList<>(filteredData);

        sortedData.comparatorProperty().bind(customerTable.comparatorProperty());

        comboFiltroOrdenacao.setOnAction(e -> {
            String selecao = comboFiltroOrdenacao.getValue();
            if ("Maior Dívida".equals(selecao)) {
                obsList.sort((c1, c2) -> Double.compare(c2.getTotalDevendo(), c1.getTotalDevendo()));
            } else if ("Menor Dívida".equals(selecao)) {
                obsList.sort((c1, c2) -> Double.compare(c1.getTotalDevendo(), c2.getTotalDevendo()));
            } else {
                obsList.sort((c1, c2) -> Integer.compare(c1.getIdCliente(), c2.getIdCliente()));
            }
        });

        customerTable.setItems(sortedData);
        aplicarFiltros();
    }

    private void showFiadoHistory(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/FiadoHistory.fxml"));
            Parent parent = loader.load();

            FiadoHistoryController controller = loader.getController();
            controller.setHistoryData(customer, new OrderService());

            Stage stage = new Stage();
            stage.setTitle("Histórico de Pagamentos - " + customer.getNomeCliente());
            stage.setScene(new Scene(parent));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.show();
        } catch (IOException e) {
            Alerts.showAlert("Erro", "Erro ao carregar histórico", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void initButtons() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");
            private final Button btnFiado = new Button("Histórico");

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                btnFiado.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
                btnEdit.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
                btnDelete.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");

                btnEdit.setOnAction(event -> {
                    Customer obj = getTableView().getItems().get(getIndex());
                    onEditAction(obj);
                });

                btnDelete.setOnAction(event -> {
                    Customer obj = getTableView().getItems().get(getIndex());
                    onDeleteAction(obj);
                });

                btnFiado.setOnAction(event -> {
                    Customer obj = getTableView().getItems().get(getIndex());
                    showFiadoHistory(obj);
                });

                HBox pane = new HBox(8, btnEdit, btnDelete, btnFiado);
                pane.setStyle("-fx-alignment: CENTER;");
                setGraphic(pane);
            }
        });
    }

    private void onEditAction(Customer obj) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/CustomerForm.fxml"));
            Parent parent = loader.load();

            CustomerFormController controller = loader.getController();
            controller.setCustomer(obj);
            controller.setServices(this.service, this);
            controller.updateFormData();

            Stage stage = new Stage();
            stage.setTitle("Editar Cliente - " + obj.getNomeCliente());
            stage.setScene(new Scene(parent));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            updateTableView();
        } catch (IOException e) {
            Alerts.showAlert("Erro", "Erro ao carregar formulário", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void onDeleteAction(Customer obj) {
        if (Alerts.showConfirmation("Confirmação", "Excluir Cliente", "Tem certeza que deseja excluir " + obj.getNomeCliente() + "?")) {
            try {
                service.deleteById(obj.getIdCliente());
                updateTableView();
            } catch (Exception e) {
                Alerts.showAlert("Erro", "Erro ao excluir", e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }
}