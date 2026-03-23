package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.CustomerService;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.ViewLoader;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class MainController {

    @FXML private Button botaoProdutos;
    @FXML private Button botaoClientes;

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

    public void onButtonCustomerListAction() {
        ViewLoader.showView(true, "/com/casarural/sistemapdv/view/customer_list.fxml", "Lista de Cliente",
                (CustomerListController controller) -> {
                    controller.setCustomerService(new CustomerService(DaoFactory.createCustomerDAO()));
                    controller.updateTableView();
                }
        );
    }

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
}