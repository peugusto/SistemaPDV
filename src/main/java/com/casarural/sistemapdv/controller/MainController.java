package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.ViewLoader;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private MenuBar menuPrincipal;
    @FXML private MenuItem menuSair;
    @FXML private MenuItem menuConfiguracoes;

    @FXML private Label labelData;
    @FXML private Label labelHora;

    @FXML private Button botaoPDV;
    @FXML private Button botaoProdutos;
    @FXML private Button botaoConsultarProdutos;
    @FXML private Button botaoClientes;
    @FXML private Button botaoFiados;
    @FXML private Button botaoHistorico;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        iniciarRelogio();
        configurarAtalhos();
    }

    private void iniciarRelogio() {
        DateTimeFormatter formatadorData = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter formatadorHora = DateTimeFormatter.ofPattern("HH:mm:ss");

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime agora = LocalDateTime.now();
            labelData.setText(agora.format(formatadorData));
            labelHora.setText(agora.format(formatadorHora));
        }));

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void configurarAtalhos() {
        botaoPDV.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F1) {
                        onBotaoPDVAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F2) {
                        onButtonProductAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F3) {
                        onButtonProductListAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F4) {
                        onButtonCustomerAction();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F5) {
                        onButtonCustomerListAction();
                        event.consume();
                    }
                });
            }
        });
    }

    @FXML
    public void onButtonProductAction() {
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/CadastroProduto.fxml", "Cadastro de Produto",
                (CadastroProdutosController controller) -> {
                    controller.setProductService(new ProductService(DaoFactory.createProductDAO()));
                    controller.setProduct(new Product());
                }
        );
    }

    @FXML
    public void onButtonCustomerAction() {
        ViewLoader.showView(false, "/com/casarural/sistemapdv/view/RegisterCustomer.fxml", "Cadastro de Cliente",
                (RegisterCustomerController controller) -> {
                    controller.setCustomerService(new CustomerService(DaoFactory.createCustomerDAO()));
                    controller.setCustomer(new Customer());
                }
        );
    }

    @FXML
    public void onButtonCustomerListAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/customer_list.fxml", "Lista de Cliente",
                (CustomerListController controller) -> {
                    controller.setCustomerService(new CustomerService(DaoFactory.createCustomerDAO()));
                    controller.updateTableView();
                }
        );
    }

    @FXML
    public void onButtonProductListAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/product_list.fxml", "Lista de Produtos",
                (ProductListController controller) -> {
                    controller.setProductService(new ProductService(DaoFactory.createProductDAO()));
                    controller.updateTableView();
                }
        );
    }

    @FXML
    public void onBotaoPDVAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/pdv.fxml", "Caixa Aberto",
                (PDVController controller) -> {
                    controller.setProductService(new ProductService(DaoFactory.createProductDAO()));
                }
        );
    }

    @FXML
    public void onMenuDashboardVendasAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/dashboard_vendas.fxml", "Dashboard de Vendas");
    }

    @FXML
    public void onBotaoHistoricoAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/order-list.fxml", "Historico de Vendas"
        );
    }
}