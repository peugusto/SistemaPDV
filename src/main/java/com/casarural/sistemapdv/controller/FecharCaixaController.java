package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.CashMovement;
import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.model.entities.CashSummary;
import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.CashRegisterService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Constraints;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Tema;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/** Fechamento do caixa: resumo calculado pelo sistema + contagem opcional da gaveta. */
public class FecharCaixaController implements Initializable {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Label labelAberto;
    @FXML private Label valTroco;
    @FXML private Label valVendasDinheiro;
    @FXML private Label valFiadoDinheiro;
    @FXML private Label valReforcos;
    @FXML private Label valSangrias;
    @FXML private Label valEsperado;
    @FXML private Label valPix;
    @FXML private Label valCartao;
    @FXML private Label valFiadoVendido;
    @FXML private Label valTotalVendido;
    @FXML private Label valFiadoRecebido;
    @FXML private ListView<String> listaMovimentos;
    @FXML private TextField campoContado;
    @FXML private Label labelDiferenca;
    @FXML private TextField campoObservacao;

    private CashRegisterService service;
    private CashRegister caixa;
    private CashSummary resumo;
    private boolean concluido = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Constraints.setTextFieldMoney(campoContado);
        Constraints.setTextFieldMaxLength(campoObservacao, 150);
        campoContado.textProperty().addListener((obs, antigo, novo) -> atualizarDiferenca());
    }

    public void configurar(CashRegisterService service, CashRegister caixa) {
        this.service = service;
        this.caixa = caixa;
        this.resumo = service.summarize(caixa);

        labelAberto.setText("Aberto em " + DATA.format(caixa.getAbertoEm()));
        valTroco.setText(Money.format(resumo.getValorInicial()));
        valVendasDinheiro.setText(Money.format(resumo.getVendas(PaymentMethod.DINHEIRO)));
        valFiadoDinheiro.setText(Money.format(resumo.getRecebimentosFiado(PaymentMethod.DINHEIRO)));
        valReforcos.setText(Money.format(resumo.getReforcos()));
        valSangrias.setText(Money.format(resumo.getSangrias()));
        valEsperado.setText(Money.format(resumo.getDinheiroEsperado()));
        valPix.setText(Money.format(resumo.getTotalPix()));
        valCartao.setText(Money.format(resumo.getTotalCartao()));
        valFiadoVendido.setText(Money.format(resumo.getVendidoNoFiado()));
        valTotalVendido.setText(Money.format(resumo.getTotalVendido()));
        valFiadoRecebido.setText(Money.format(resumo.getTotalFiadoRecebido()));

        listaMovimentos.setItems(FXCollections.observableArrayList(
                service.findMovements(caixa).stream().map(FecharCaixaController::descrever).collect(Collectors.toList())));
        campoContado.requestFocus();
    }

    public boolean isConcluido() {
        return concluido;
    }

    private static String descrever(CashMovement m) {
        String tipo = m.getTipo() == CashMovementType.SANGRIA ? "Retirada" : "Reforço";
        String sinal = m.getTipo() == CashMovementType.SANGRIA ? "− " : "+ ";
        return String.format("%s  %s  %s%s%s", HORA.format(m.getDataMovimento()), tipo, sinal, Money.format(m.getValor()),
                m.getMotivo() == null ? "" : "  (" + m.getMotivo() + ")");
    }

    /** null = não contou. */
    private BigDecimal valorContado() {
        String texto = campoContado.getText();
        if (texto == null || texto.trim().isEmpty()) return null;
        try {
            return Money.parse(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void atualizarDiferenca() {
        BigDecimal contado = valorContado();
        if (contado == null) {
            labelDiferenca.setText("Não conferido");
            Tema.estado(labelDiferenca, null);
            return;
        }
        BigDecimal dif = resumo.diferenca(contado);
        if (dif.signum() == 0) {
            labelDiferenca.setText("Bateu certinho");
            Tema.estado(labelDiferenca, "texto-sucesso");
        } else if (dif.signum() < 0) {
            labelDiferenca.setText("Faltam " + Money.format(dif.negate()));
            Tema.estado(labelDiferenca, "texto-perigo");
        } else {
            labelDiferenca.setText("Sobram " + Money.format(dif));
            Tema.estado(labelDiferenca, "texto-alerta");
        }
    }

    @FXML
    public void onFecharCaixa() {
        BigDecimal contado = valorContado();
        String pergunta = contado == null
                ? "Fechar o caixa sem contar a gaveta?\n\nDeve ter na gaveta: " + Money.format(resumo.getDinheiroEsperado())
                : "Fechar o caixa?\n\nDeve ter: " + Money.format(resumo.getDinheiroEsperado())
                  + "\nContado: " + Money.format(contado) + "\n" + labelDiferenca.getText();
        if (!Alerts.showConfirmation("Fechar caixa", "Depois de fechado, para vender será preciso abrir um novo caixa.", pergunta)) {
            return;
        }
        try {
            service.close(caixa, contado, campoObservacao.getText());
            concluido = true;
            ((Stage) campoContado.getScene().getWindow()).close();
            Alerts.showAlert("Caixa fechado", null, "Caixa fechado. O resumo fica disponível em Histórico de caixas.", Alert.AlertType.INFORMATION);
        } catch (DbException e) {
            Alerts.showAlert("Fechar caixa", null, e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void onCancelar() {
        ((Stage) campoContado.getScene().getWindow()).close();
    }
}
