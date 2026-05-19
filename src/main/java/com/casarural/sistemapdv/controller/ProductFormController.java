package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class ProductFormController implements Initializable {

    private Product entity;
    private ProductService service;
    private ProductListController listController;

    @FXML private TextField txtCodBarras;
    @FXML private TextField txtNome;
    @FXML private TextField txtPreco;
    @FXML private TextField txtEstoque;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    public void setProduct(Product entity) {
        this.entity = entity;
    }

    public void setServices(ProductService service, ProductListController listController) {
        this.service = service;
        this.listController = listController;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }


    public void updateFormData() {
        if (entity == null) {
            throw new IllegalStateException("Entity foi enviada nula");
        }
        txtCodBarras.setText(entity.getCodBarras());
        txtNome.setText(entity.getNomeProduto());
        txtPreco.setText(String.valueOf(entity.getPrecoProduto()));
        txtEstoque.setText(String.valueOf(entity.getEstoque()));
    }

    @FXML
    public void onBtnSaveAction(ActionEvent event) {
        if (entity == null || service == null) {
            throw new IllegalStateException("Service ou Entity nulos");
        }


        if (txtNome.getText().trim().isEmpty() || txtCodBarras.getText().trim().isEmpty()) {
            Alerts.showAlert("Erro de Validação", null, "Os campos Nome e Código de Barras são obrigatórios.", Alert.AlertType.ERROR);
            return;
        }

        try {

            String codBarras = txtCodBarras.getText();
            String nome = txtNome.getText();
            double preco = Double.parseDouble(txtPreco.getText());
            int estoque = Integer.parseInt(txtEstoque.getText());


            entity.setCodBarras(codBarras);
            entity.setNomeProduto(nome);
            entity.setPrecoProduto(preco);
            entity.setEstoque(estoque);


            service.saveOrUpdate(entity);


            listController.updateTableView();


            closeStage(event);

            Alerts.showAlert("Sucesso", null, "Produto atualizado com sucesso!", Alert.AlertType.INFORMATION);

        } catch (NumberFormatException e) {
            Alerts.showAlert("Erro de Tipo", null, "Certifique-se de preencher Preço e Estoque com valores numéricos válidos.", Alert.AlertType.ERROR);
        } catch (Exception e) {
            Alerts.showAlert("Erro ao Salvar", "Erro no banco de dados", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void onBtnCancelAction(ActionEvent event) {
        closeStage(event);
    }

    private void closeStage(ActionEvent event) {
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }
}