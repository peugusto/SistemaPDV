package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.services.ProductService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.ProdutoCampos;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
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
    @FXML private CheckBox chkPermiteFiado;
    @FXML private CheckBox chkAtivo;
    @FXML private CheckBox chkPesavel;
    @FXML private CheckBox chkControlaEstoque;
    @FXML private Label lblPreco;
    @FXML private TextField txtCusto;
    @FXML private Label lblCusto;
    @FXML private Label lblMargem;
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
        Constraints.setTextFieldMoney(txtPreco);
        Constraints.setTextFieldMaxLength(txtNome, Product.TAMANHO_MAX_NOME);
        Constraints.setTextFieldMaxLength(txtCodBarras, Product.TAMANHO_MAX_CODIGO);
        ProdutoCampos.ligar(chkPesavel, chkControlaEstoque, txtEstoque, lblPreco);
        ProdutoCampos.ligarCusto(chkPesavel, txtPreco, txtCusto, lblCusto, lblMargem);
    }


    public void updateFormData() {
        if (entity == null) {
            throw new IllegalStateException("Entity foi enviada nula");
        }
        txtCodBarras.setText(entity.getCodBarras());
        txtNome.setText(entity.getNomeProduto());
        txtPreco.setText(Money.toInput(entity.getPrecoProduto()));
        txtEstoque.setText(String.valueOf(entity.getEstoque()));
        chkPermiteFiado.setSelected(entity.isPermiteFiado());
        chkAtivo.setSelected(entity.isAtivo());
        chkControlaEstoque.setSelected(entity.isControlaEstoque());
        chkPesavel.setSelected(entity.isPesavel());
        txtCusto.setText(entity.getPrecoCusto() == null ? "" : Money.toInput(entity.getPrecoCusto()));
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
            BigDecimal preco = Money.parse(txtPreco.getText());
            int estoque = ProdutoCampos.estoque(txtEstoque);


            entity.setCodBarras(codBarras);
            entity.setNomeProduto(nome);
            entity.setPrecoProduto(preco);
            entity.setEstoque(estoque);
            entity.setPermiteFiado(chkPermiteFiado.isSelected());
            entity.setAtivo(chkAtivo.isSelected());
            entity.setTipoVenda(chkPesavel.isSelected() ? TipoVenda.PESAVEL : TipoVenda.UNIDADE);
            entity.setControlaEstoque(chkControlaEstoque.isSelected());
            entity.setPrecoCusto(ProdutoCampos.lerValor(txtCusto));


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