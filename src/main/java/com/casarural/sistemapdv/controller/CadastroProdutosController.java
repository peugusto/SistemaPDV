package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.EntradaEstoque;
import com.casarural.sistemapdv.db.EntradaEstoqueNaoAplicadaException;
import com.casarural.sistemapdv.db.ResultadoTransacaoIncertoException;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.net.URL;
import java.math.BigDecimal;
import java.util.ResourceBundle;
import java.util.UUID;

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
    @FXML private TextField txtCusto;
    @FXML private Label lblCusto;
    @FXML private Label lblMargem;
    @FXML private Button botaoSalvar;
    @FXML private Button botaoCancelar;

    private boolean isExistingProduct = false;
    private EntradaEstoque entradaPendente;
    private boolean entradaIncerta;
    private boolean salvamentoConfirmado;
    private boolean fechamentoProtegido;

    public void setProductService(ProductService service) {
        this.service = service;
    }

    public void setProduct(Product entity) {
        this.entity = entity;
    }


    @FXML
    public void onBotaoCancelarAction() {
        if (entradaIncerta) {
            avisarEntradaIncerta();
            return;
        }
        if (Alerts.showConfirmation("Sair", "Deseja voltar ao menu?", "Dados não salvos serão perdidos.")) {
            closeStage();
        }
    }

    private void closeStage() {
        Stage stage = (Stage) botaoCancelar.getScene().getWindow();
        stage.close();
    }

    private void onSearchProduct() {
        if (entradaIncerta) {
            avisarEntradaIncerta();
            return;
        }
        entradaPendente = null;
        String code = txtCodBarras.getText();
        if (code.trim().isEmpty()) return;

        service.findByCodBarras(code).ifPresentOrElse(
                product -> {
                    if (!product.isAtivo()) {
                        Alerts.showAlertAndWait("Produto desativado", product.getNomeProduto(),
                                "Este produto está desativado. Para voltar a vender e dar entrada, "
                                        + "marque \"Produto ativo\" em Estoque > Editar.", AlertType.INFORMATION);
                        txtCodBarras.clear();
                        return;
                    }
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

                    txtCodBarras.setText(product.getCodBarras());
                    txtNome.setText(product.getNomeProduto());
                    txtPreco.setText(Money.toInput(product.getPrecoProduto()));
                    chkPermiteFiado.setSelected(product.isPermiteFiado());
                    // Entrada de mercadoria: o custo vem com o último pago; se mudou, é só corrigir
                    txtCusto.setText(product.getPrecoCusto() == null ? "" : Money.toInput(product.getPrecoCusto()));

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
        if (salvamentoConfirmado) {
            closeStage();
            return;
        }
        try {
            if (isExistingProduct) {
                salvarEntrada();
            } else {
                Product novo = novoProdutoDoFormulario();
                service.saveOrUpdate(novo);
                entity = novo;
            }
            salvamentoConfirmado = true;
            entradaIncerta = false;
        } catch (ResultadoTransacaoIncertoException e) {
            entradaIncerta = true;
            bloquearEntrada(true);
            protegerFechamento();
            avisarEntradaIncerta();
            return;
        } catch (EntradaEstoqueNaoAplicadaException e) {
            entradaIncerta = false;
            bloquearEntrada(false);
            Alerts.showAlert("Entrada não aplicada", null, e.getMessage(), AlertType.ERROR);
            return;
        } catch (Exception e) {
            if (entradaIncerta) {
                avisarEntradaIncerta();
                return;
            }
            Alerts.showAlert("Erro", null, e.getMessage(), AlertType.ERROR);
            return;
        }
        // Uma falha da interface depois do sucesso não transforma a gravação em nova tentativa.
        Alerts.showAlert("Sucesso", null, "Operação realizada!", AlertType.INFORMATION);
        closeStage();
    }

    private void salvarEntrada() {
        String codigo = txtCodBarras.getText().trim();
        if (entity == null || !codigo.equals(entity.getCodBarras())) {
            throw new IllegalArgumentException(
                    "O código foi alterado. Aperte Enter para pesquisar o produto antes de salvar.");
        }
        int quantidade = quantidadeDoFormulario(true);
        BigDecimal custo = custoDoFormulario();
        // O valor apenas exibido não deve sobrescrever um custo alterado por outro terminal.
        if (custo != null && entity.getPrecoCusto() != null && custo.compareTo(entity.getPrecoCusto()) == 0) {
            custo = null;
        }
        if (entradaIncerta) {
            if (entradaPendente == null || !entradaPendente.mesmosDados(entity.getIdProduto(), quantidade, custo)) {
                throw new IllegalArgumentException("Confirme a entrada pendente sem alterar seus dados.");
            }
            service.registrarEntrada(entradaPendente);
            return;
        }
        if (entradaPendente == null || !entradaPendente.mesmosDados(entity.getIdProduto(), quantidade, custo)) {
            entradaPendente = new EntradaEstoque(UUID.randomUUID(), entity.getIdProduto(), quantidade, custo);
        }
        service.registrarEntrada(entradaPendente);
    }

    private Product novoProdutoDoFormulario() {
        Product novo = new Product();
        novo.setCodBarras(txtCodBarras.getText().trim());
        novo.setNomeProduto(txtNome.getText());
        novo.setPrecoProduto(Money.parse(txtPreco.getText()));
        novo.setEstoque(quantidadeDoFormulario(false));
        novo.setPrecoCusto(custoDoFormulario());
        novo.setPermiteFiado(chkPermiteFiado.isSelected());
        novo.setTipoVenda(chkPesavel.isSelected() ? TipoVenda.PESAVEL : TipoVenda.UNIDADE);
        novo.setControlaEstoque(chkControlaEstoque.isSelected());
        return novo;
    }

    private int quantidadeDoFormulario(boolean entrada) {
        try {
            int quantidade = ProdutoCampos.estoque(txtEstoque);
            if (quantidade < 0 || (entrada && quantidade == 0)) throw new NumberFormatException();
            return quantidade;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(entrada
                    ? "Digite uma quantidade de entrada inteira entre 1 e 2.147.483.647."
                    : "Digite um estoque inicial inteiro entre 0 e 2.147.483.647.");
        }
    }

    private BigDecimal custoDoFormulario() {
        if (txtCusto.getText() == null || txtCusto.getText().isBlank()) return null;
        try {
            return EntradaEstoque.validarCusto(Money.parse(txtCusto.getText()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Custo inválido. Digite um valor como 12,50 ou deixe vazio.");
        }
    }

    private void bloquearEntrada(boolean bloquear) {
        txtCodBarras.setDisable(bloquear);
        txtEstoque.setDisable(bloquear);
        txtCusto.setDisable(bloquear);
    }

    private void avisarEntradaIncerta() {
        Alerts.showAlert("Confirmar entrada", "Ainda não foi possível confirmar esta entrada.",
                "Ela pode já ter sido gravada. Clique em Salvar novamente para confirmar a mesma entrada, "
                        + "sem duplicar a quantidade. Aguarde a confirmação antes de fechar esta tela.", AlertType.WARNING);
    }

    private void protegerFechamento() {
        if (fechamentoProtegido || botaoCancelar.getScene() == null) return;
        Stage stage = (Stage) botaoCancelar.getScene().getWindow();
        if (stage == null) return;
        // O ViewLoader fecha programaticamente pelo Esc, sem disparar WINDOW_CLOSE_REQUEST.
        botaoCancelar.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (entradaIncerta && event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                avisarEntradaIncerta();
            }
        });
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (entradaIncerta) {
                event.consume();
                avisarEntradaIncerta();
            }
        });
        fechamentoProtegido = true;
    }

    private void enableFields() {
        entity = new Product();
        isExistingProduct = false;
        entradaPendente = null;
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
        txtCusto.clear();
        txtEstoque.clear();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Constraints.setTextFieldInteger(txtEstoque);
        Constraints.setTextFieldMoney(txtPreco);
        Constraints.setTextFieldMaxLength(txtNome, Product.TAMANHO_MAX_NOME);
        Constraints.setTextFieldMaxLength(txtCodBarras, Product.TAMANHO_MAX_CODIGO);

        ProdutoCampos.ligar(chkPesavel, chkControlaEstoque, txtEstoque, lblPreco);
        ProdutoCampos.ligarCusto(chkPesavel, txtPreco, txtCusto, lblCusto, lblMargem);

        txtCodBarras.setOnAction(event -> onSearchProduct());
    }
}
