package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.ProdutoCampos;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class CadastroProdutosController implements Initializable {


    private ProductService service;
    private Product entity;

    @FXML private TextField txtCodBarras;
    @FXML private TextField txtNome;
    @FXML private TextField txtPreco;
    @FXML private TextField txtEstoque;
    @FXML private CheckBox chkPermiteFiado;
    @FXML private CheckBox chkPesavel;
    @FXML private CheckBox chkControlaEstoque;
    @FXML private Label lblPreco;
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
            entity.setPrecoProduto(Money.parse(txtPreco.getText()));
            entity.setEstoque(ProdutoCampos.estoque(txtEstoque));
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
                    // Sem controle de estoque não há entrada para dar; o resto muda em "Editar"
                    if (!product.isControlaEstoque()) {
                        Alerts.showAlertAndWait("Produto sem estoque", product.getNomeProduto(),
                                (product.isPesavel() ? "Este produto é pesável" : "Este produto está sem controle de estoque")
                                        + ", então não precisa dar entrada.\n\nPara mudar preço ou nome, use Estoque > Editar.",
                                AlertType.INFORMATION);
                        txtCodBarras.clear();
                        return;
                    }
                    this.entity = product;
                    isExistingProduct = true;

                    txtNome.setText(product.getNomeProduto());
                    txtPreco.setText(Money.toInput(product.getPrecoProduto()));
                    chkPermiteFiado.setSelected(product.isPermiteFiado());

                    // Produto existente: aqui só entra estoque; nome, preço e fiado mudam em "Editar"
                    txtNome.setEditable(false);
                    txtPreco.setEditable(false);
                    chkPermiteFiado.setDisable(true);
                    chkPesavel.setDisable(true);
                    chkControlaEstoque.setDisable(true);
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
        entity.setPrecoProduto(Money.parse(txtPreco.getText()));

        int qtdInformada = ProdutoCampos.estoque(txtEstoque);

        if (isExistingProduct) {
            entity.adicionarEstoque(qtdInformada);
        } else {
            entity.setEstoque(qtdInformada);
            entity.setPermiteFiado(chkPermiteFiado.isSelected());
            entity.setTipoVenda(chkPesavel.isSelected() ? TipoVenda.PESAVEL : TipoVenda.UNIDADE);
            entity.setControlaEstoque(chkControlaEstoque.isSelected());
        }
    }

    private void enableFields() {
        isExistingProduct = false;
        txtNome.setEditable(true);
        txtPreco.setEditable(true);
        chkPermiteFiado.setDisable(false);
        chkPermiteFiado.setSelected(true);
        chkPesavel.setDisable(false);
        chkPesavel.setSelected(false);
        chkControlaEstoque.setSelected(true);
        chkControlaEstoque.setDisable(false);
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
        Constraints.setTextFieldMoney(txtPreco);
        Constraints.setTextFieldMaxLength(txtNome,20);

        ProdutoCampos.ligar(chkPesavel, chkControlaEstoque, txtEstoque, lblPreco);

        txtCodBarras.setOnAction(event -> onSearchProduct());
    }
}