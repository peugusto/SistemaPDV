package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import javafx.fxml.FXML;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CadastroProdutosController {

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

    // Método para injetar o Service (será usado pelo ViewLoader ou MainController)
    public void setProductService(ProductService service) {
        this.service = service;
    }

    // Método para injetar a entidade (útil para quando for Editar um produto)
    public void setProduct(Product entity) {
        this.entity = entity;
    }

    @FXML
    public void onBotaoSalvarAction() {
        if (service == null) {
            throw new IllegalStateException("Service estava nulo");
        }

        try {
            // 1. Pega os dados dos campos e coloca na entidade
            Product obj = getFormData();

            // 2. Salva via Service
            service.saveOrUpdate(obj);

            Alerts.showAlert("Sucesso", null, "Produto salvo com sucesso!", AlertType.INFORMATION);

            // 3. Fecha a janela após salvar
            onBotaoCancelarAction();

        } catch (Exception e) {
            Alerts.showAlert("Erro ao salvar", null, e.getMessage(), AlertType.ERROR);
        }
    }

    private Product getFormData() {
        // Validação básica de campos vazios
        if (txtCodBarras.getText().trim().isEmpty() || txtNome.getText().trim().isEmpty()) {
            throw new RuntimeException("Campos obrigatórios não preenchidos.");
        }

        // Se for um novo produto, entity pode estar nula
        if (entity == null) {
            entity = new Product();
        }

        entity.setCodBarras(txtCodBarras.getText());
        entity.setNomeProduto(txtNome.getText());

        // Converte Strings para os tipos numéricos (com tratamento básico)
        try {
            entity.setPrecoProduto(Double.parseDouble(txtPreco.getText().replace(",", ".")));
            // Ajuste aqui se o seu setEstoque for de incremento ou atribuição direta
            entity.setEstoque(Integer.parseInt(txtEstoque.getText()));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Preço ou Estoque inválidos.");
        }

        return entity;
    }

    @FXML
    public void onBotaoCancelarAction() {
        Stage stage = (Stage) botaoCancelar.getScene().getWindow();
        stage.close();
    }
}