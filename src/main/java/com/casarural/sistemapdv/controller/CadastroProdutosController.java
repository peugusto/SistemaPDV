package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class CadastroProdutosController implements Initializable {

    // Dependência do Service
    private ProductService service;

    // Entidade que será salva
    private Product entity;

    @FXML private TextField txtCodBarras;
    @FXML private TextField txtNome;
    @FXML private TextField txtPreco;
    @FXML private TextField txtEstoque;
    @FXML private Button botaoSalvar;
    @FXML private Button botaoCancelar;

    private boolean isExistingProduct = false;

    public void setProductService(ProductService service) {
        this.service = service;
    }


    public void setProduct(Product entity) {
        this.entity = entity;
    }


    private Product getFormData() {

        if (txtCodBarras.getText().trim().isEmpty() || txtNome.getText().trim().isEmpty()) {
            throw new RuntimeException("Campos obrigatórios não preenchidos.");
        }


        if (entity == null) entity = new Product();

        entity.setCodBarras(txtCodBarras.getText());
        entity.setNomeProduto(txtNome.getText());


        try {
            entity.setPrecoProduto(Double.parseDouble(txtPreco.getText().replace(",", ".")));
            entity.setEstoque(Integer.parseInt(txtEstoque.getText()));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Preço ou Estoque inválidos.");
        }
        return entity;
    }

    @FXML
    public void onBotaoCancelarAction() {
        if (Alerts.showConfirmation("Sair", "Deseja voltar ao menu?", "Dados não salvos serão perdidos.")) {
            closeStage();
        }
    }

    private void closeStage() {
        Stage stage = (Stage) botaoCancelar.getScene().getWindow();
        stage.close();
    }

    private void onSearchProduct() {
        String code = txtCodBarras.getText();
        if (code.trim().isEmpty()) return;

        service.findByCodBarras(code).ifPresentOrElse(
                product -> {
                    this.entity = product;
                    isExistingProduct = true;

                    txtNome.setText(product.getNomeProduto());
                    txtPreco.setText(String.valueOf(product.getPrecoProduto()));
                    

                    txtNome.setEditable(false);
                    txtPreco.setEditable(false);
                    txtEstoque.requestFocus();
                },
                () -> {

                    if (Alerts.showConfirmation("Novo Produto", null, "Código não encontrado. Deseja cadastrar um novo produto?")) {
                        clearFormExceptBarcode();
                        enableFields();
                        txtNome.requestFocus();
                    } else {
                        txtCodBarras.clear();
                    }
                }
        );
    }

    @FXML
    public void onBotaoSalvarAction() {
        try {
            updateEntityFromForm();
            service.saveOrUpdate(entity);
            Alerts.showAlert("Sucesso", null, "Operação realizada!", AlertType.INFORMATION);
            closeStage();
        } catch (Exception e) {
            Alerts.showAlert("Erro", null, e.getMessage(), AlertType.ERROR);
        }
    }

    private void updateEntityFromForm() {
        if (entity == null) entity = new Product();

        entity.setCodBarras(txtCodBarras.getText());
        entity.setNomeProduto(txtNome.getText());
        entity.setPrecoProduto(Double.parseDouble(txtPreco.getText().replace(",", ".")));

        int qtdInformada = Integer.parseInt(txtEstoque.getText());

        if (isExistingProduct) {
            entity.adicionarEstoque(qtdInformada);
        } else {
            entity.setEstoque(qtdInformada);
        }
    }

    private void enableFields() {
        isExistingProduct = false;
        txtNome.setEditable(true);
        txtPreco.setEditable(true);
        txtNome.setStyle("");
        txtPreco.setStyle("");
    }

    private void clearFormExceptBarcode() {
        txtNome.clear();
        txtPreco.clear();
        txtEstoque.clear();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Constraints.setTextFieldInteger(txtEstoque);
        Constraints.setTextFieldDouble(txtPreco);
        Constraints.setTextFieldMaxLength(txtNome,20);

        txtCodBarras.setOnAction(event -> onSearchProduct());
    }
}