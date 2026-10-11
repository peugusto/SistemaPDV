package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.services.CashRegisterService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

/** Abrir o caixa (troco inicial), retirar dinheiro (sangria) ou colocar dinheiro (reforço). */
public class CaixaValorController implements Initializable {

    public enum Modo { ABRIR, SANGRIA, REFORCO }

    @FXML private Label labelTitulo;
    @FXML private Label labelExplicacao;
    @FXML private Label labelInfo;
    @FXML private TextField campoValor;
    @FXML private VBox boxMotivo;
    @FXML private TextField campoMotivo;
    @FXML private Button btnConfirmar;

    private Modo modo;
    private CashRegisterService service;
    private CashRegister caixa;
    private boolean concluido = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Constraints.setTextFieldMoney(campoValor);
        Constraints.setTextFieldMaxLength(campoMotivo, 150);
    }

    /** caixa: o caixa aberto (null no modo ABRIR). */
    public void configurar(Modo modo, CashRegisterService service, CashRegister caixa) {
        this.modo = modo;
        this.service = service;
        this.caixa = caixa;

        switch (modo) {
            case ABRIR:
                labelTitulo.setText("ABRIR CAIXA");
                labelExplicacao.setText("Conte o dinheiro que está na gaveta agora (o troco do dia) e digite abaixo. "
                        + "Se a gaveta estiver vazia, digite 0.");
                btnConfirmar.setText("Abrir caixa");
                esconderMotivo();
                break;
            case SANGRIA:
                labelTitulo.setText("RETIRAR DINHEIRO");
                labelExplicacao.setText("Dinheiro tirado da gaveta que não é troco de venda: pagar fornecedor, levar para casa...");
                labelInfo.setText("Na gaveta agora: " + Money.format(service.summarize(caixa).getDinheiroEsperado()));
                btnConfirmar.setText("Retirar");
                btnConfirmar.setStyle(btnConfirmar.getStyle().replace("#27ae60", "#c0392b"));
                break;
            case REFORCO:
                labelTitulo.setText("COLOCAR DINHEIRO");
                labelExplicacao.setText("Dinheiro colocado na gaveta que não veio de venda: por exemplo, mais troco.");
                labelInfo.setText("Na gaveta agora: " + Money.format(service.summarize(caixa).getDinheiroEsperado()));
                btnConfirmar.setText("Colocar");
                break;
        }
        campoValor.requestFocus();
    }

    public boolean isConcluido() {
        return concluido;
    }

    private void esconderMotivo() {
        boxMotivo.setVisible(false);
        boxMotivo.setManaged(false);
    }

    @FXML
    public void onConfirmar() {
        String texto = campoValor.getText();
        if (texto == null || texto.trim().isEmpty()) {
            Alerts.showAlert("Valor", null,
                    modo == Modo.ABRIR ? "Digite o dinheiro da gaveta (0 se estiver vazia)." : "Digite o valor.",
                    Alert.AlertType.WARNING);
            campoValor.requestFocus();
            return;
        }
        BigDecimal valor;
        try {
            valor = Money.parse(texto);
        } catch (NumberFormatException e) {
            Alerts.showAlert("Valor inválido", null, "Digite o valor, por exemplo 50,00.", Alert.AlertType.WARNING);
            return;
        }

        try {
            switch (modo) {
                case ABRIR:
                    service.open(valor);
                    break;
                case SANGRIA:
                    service.addSangria(caixa, valor, campoMotivo.getText());
                    break;
                case REFORCO:
                    service.addReforco(caixa, valor, campoMotivo.getText());
                    break;
            }
            concluido = true;
            fechar();
        } catch (DbException e) {
            Alerts.showAlert("Caixa", null, e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void onCancelar() {
        fechar();
    }

    private void fechar() {
        ((Stage) campoValor.getScene().getWindow()).close();
    }
}
