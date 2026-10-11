package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.OrderService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Tema;
import com.casarural.sistemapdv.util.Telefone;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.MoneyCells;
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
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class CustomerListController implements Initializable {

    private CustomerService service;

    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Integer> idColumn;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, CustomerStatus> creditStatusColumn;
    @FXML private TableColumn<Customer, BigDecimal> creditLimitColumn;
    @FXML private TableColumn<Customer, BigDecimal> totalDebtColumn;
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
        phoneColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getTelefone() == null ? "—" : Telefone.formatar(c.getValue().getTelefone())));
        creditStatusColumn.setCellValueFactory(new PropertyValueFactory<>("situacaoFiado"));
        creditLimitColumn.setCellValueFactory(new PropertyValueFactory<>("limiteCredito"));
        totalDebtColumn.setCellValueFactory(new PropertyValueFactory<>("totalDevendo"));

        MoneyCells.format(creditLimitColumn);

        // Situação como etiqueta colorida (classes do componentes.css)
        creditStatusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label etiqueta = new Label();
            { etiqueta.getStyleClass().add("etiqueta"); }
            @Override
            protected void updateItem(CustomerStatus status, boolean empty) {
                super.updateItem(status, empty);
                setText(null);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                Customer cliente = getTableRow() == null ? null : getTableRow().getItem();
                String texto = situacaoFiado(cliente);
                etiqueta.setText(texto);
                etiqueta.getStyleClass().removeAll("etiqueta-perigo", "etiqueta-sucesso");
                etiqueta.getStyleClass().add("Liberado".equals(texto) ? "etiqueta-sucesso" : "etiqueta-perigo");
                setGraphic(etiqueta);
            }
        });

        totalDebtColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    Tema.estado(this, null);
                } else {
                    BigDecimal debt = Money.of(item);
                    setText(Money.isPositive(debt) ? Money.format(debt) : "Nada");
                    Tema.estado(this, Money.isPositive(debt) ? "texto-perigo" : "texto-sucesso");
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
        comboFiltroStatus.setItems(FXCollections.observableArrayList("Todos", "Liberado", "Bloqueado", "Em atraso"));
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
                    situacaoFiado(customer).equals(statusFilter);

            return matchesSearch && matchesStatus;
        });
    }

    /**
     * "Em atraso" (bloqueio automático, só sai pagando) tem prioridade sobre "Bloqueado"
     * (bloqueio manual do cadastro); senão "Liberado".
     */
    private static String situacaoFiado(Customer c) {
        if (c == null) return "";
        if (c.isBloqueadoPorAtraso()) return "Em atraso";
        return c.getSituacaoFiado() == CustomerStatus.BLOQUEADO ? "Bloqueado" : "Liberado";
    }

    private void showFiadoDetails(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/FiadoList.fxml"));
            Parent parent = loader.load();

            FiadoListController controller = loader.getController();
            controller.setCustomerData(customer, new OrderService());

            Stage stage = new Stage();
            stage.setTitle("Conta de fiado - " + customer.getNomeCliente());
            Scene scene = new Scene(parent);
            Tema.aplicar(scene);
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            updateTableView();
        } catch (Exception e) {
            Alerts.showAlert("Erro", "Erro ao abrir detalhes", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void updateTableView() {
        if (service == null) return;

        // findAll já traz o total devendo de cada cliente (calculado no SQL)
        List<Customer> list = service.findAll();

        obsList = FXCollections.observableArrayList(list);
        filteredData = new FilteredList<>(obsList, b -> true);

        SortedList<Customer> sortedData = new SortedList<>(filteredData);

        sortedData.comparatorProperty().bind(customerTable.comparatorProperty());

        comboFiltroOrdenacao.setOnAction(e -> {
            String selecao = comboFiltroOrdenacao.getValue();
            if ("Maior Dívida".equals(selecao)) {
                obsList.sort((c1, c2) -> c2.getTotalDevendo().compareTo(c1.getTotalDevendo()));
            } else if ("Menor Dívida".equals(selecao)) {
                obsList.sort((c1, c2) -> c1.getTotalDevendo().compareTo(c2.getTotalDevendo()));
            } else {
                obsList.sort((c1, c2) -> Integer.compare(c1.getIdCliente(), c2.getIdCliente()));
            }
        });

        customerTable.setItems(sortedData);
        aplicarFiltros();
    }

    private void initButtons() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Editar");
            private final Button btnDelete = new Button("Excluir");
            // Abre a conta de fiado (o mesmo que dar dois cliques na linha)
            private final Button btnFiado = new Button("Fiado");

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                btnFiado.getStyleClass().setAll("button", "botao-pequeno", "botao-primario");
                btnEdit.getStyleClass().setAll("button", "botao-pequeno", "botao-info");
                btnDelete.getStyleClass().setAll("button", "botao-pequeno", "botao-perigo");

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
                    showFiadoDetails(obj);
                });

                HBox pane = new HBox(8, btnFiado, btnEdit, btnDelete);
                pane.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
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
            Scene scene = new Scene(parent);
            Tema.aplicar(scene);
            stage.setScene(scene);
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
                Alerts.showAlert("Excluir cliente", "Não foi possível excluir " + obj.getNomeCliente(), e.getMessage(), Alert.AlertType.WARNING);
            }
        }
    }
}