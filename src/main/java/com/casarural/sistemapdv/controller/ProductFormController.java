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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

public class ProductFormController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ProductFormController.class);
    private Product entity;
    private ProductService service;
    private ProductListController listController;
    private boolean salvamentoConfirmado;

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
        if (salvamentoConfirmado) {
            closeStage(event);
            return;
        }
        if (entity == null || service == null) {
            throw new IllegalStateException("Service ou Entity nulos");
        }


        if (txtNome.getText().trim().isEmpty() || txtCodBarras.getText().trim().isEmpty()) {
            Alerts.showAlert("Erro de Validação", null, "Os campos Nome e Código de Barras são obrigatórios.", Alert.AlertType.ERROR);
            return;
        }

        try {
            Product alterado = produtoDoFormulario();
            service.saveOrUpdate(alterado);
            entity = alterado;
            salvamentoConfirmado = true;
        } catch (IllegalArgumentException e) {
            Alerts.showAlert("Erro de Validação", "Confira os dados do produto", e.getMessage(), Alert.AlertType.ERROR);
            return;
        } catch (Exception e) {
            Alerts.showAlert("Erro ao Salvar", "Não foi possível salvar o produto", e.getMessage(), Alert.AlertType.ERROR);
            return;
        }

        // O produto já foi gravado: erro ao consultar a lista ou fechar a tela não permite gravá-lo outra vez.
        try {
            btnSave.setDisable(true);
            listController.updateTableView();
            closeStage(event);
            Alerts.showAlert("Sucesso", null, "Produto atualizado com sucesso!", Alert.AlertType.INFORMATION);
        } catch (RuntimeException e) {
            log.warn("Produto #{} salvo; falha ao atualizar a interface", entity.getIdProduto(), e);
            Alerts.showAlert("Produto salvo", "O produto foi salvo, mas a tela não pôde ser atualizada.",
                    "Não salve novamente. Feche esta janela e abra a lista de produtos de novo para conferir a alteração.",
                    Alert.AlertType.WARNING);
        }
    }

    private Product produtoDoFormulario() {
        BigDecimal preco;
        try {
            preco = Money.parse(txtPreco.getText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Preço inválido. Digite um valor como 12,50.", e);
        }
        int estoque = ProdutoCampos.estoque(txtEstoque);
        BigDecimal custo = ProdutoCampos.lerValorParaSalvar(txtCusto);
        // O objeto recebido pertence à lista. Uma falha ou Cancelar deve deixá-lo intacto.
        Product alterado = new Product(txtCodBarras.getText(), txtNome.getText(), preco, estoque);
        alterado.setIdProduto(entity.getIdProduto());
        alterado.copiarEstadoOriginal(entity);
        alterado.setPermiteFiado(chkPermiteFiado.isSelected());
        alterado.setAtivo(chkAtivo.isSelected());
        alterado.setTipoVenda(chkPesavel.isSelected() ? TipoVenda.PESAVEL : TipoVenda.UNIDADE);
        alterado.setControlaEstoque(chkControlaEstoque.isSelected());
        alterado.setPrecoCusto(custo);
        return alterado;
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
