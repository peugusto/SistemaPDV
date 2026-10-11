package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.model.entities.CashSummary;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.CashRegisterService;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.MoneyCells;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/** Lista de caixas fechados e o resumo do caixa selecionado. */
public class HistoricoCaixaController implements Initializable {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private TableView<CashRegister> tabela;
    @FXML private TableColumn<CashRegister, LocalDateTime> colAbertura;
    @FXML private TableColumn<CashRegister, LocalDateTime> colFechamento;
    @FXML private TableColumn<CashRegister, BigDecimal> colTroco;
    @FXML private TableColumn<CashRegister, BigDecimal> colEsperado;
    @FXML private TableColumn<CashRegister, String> colContado;
    @FXML private TableColumn<CashRegister, String> colDiferenca;
    @FXML private Label labelResumo;

    private CashRegisterService service;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colAbertura.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getAbertoEm()));
        colFechamento.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFechadoEm()));
        formatarData(colAbertura);
        formatarData(colFechamento);
        colTroco.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getValorInicial()));
        colEsperado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getValorEsperado()));
        MoneyCells.format(colTroco);
        MoneyCells.format(colEsperado);
        colContado.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getValorContado() == null ? "—" : Money.format(c.getValue().getValorContado())));
        colDiferenca.setCellValueFactory(c -> new SimpleStringProperty(resultado(c.getValue())));
        colDiferenca.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (empty || item == null) {
                    setStyle("");
                } else if (item.startsWith("Faltaram")) {
                    setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                } else if (item.startsWith("Sobraram")) {
                    setStyle("-fx-text-fill: #d35400; -fx-font-weight: bold;");
                } else if (item.startsWith("Bateu")) {
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #7f8c8d;");
                }
            }
        });
        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antigo, caixa) -> mostrarResumo(caixa));
    }

    public void configurar(CashRegisterService service) {
        this.service = service;
        tabela.setItems(FXCollections.observableArrayList(service.findClosed(90)));
    }

    private static String resultado(CashRegister c) {
        BigDecimal dif = c.getDiferenca();
        if (dif == null) return "Não conferido";
        if (dif.signum() == 0) return "Bateu";
        return dif.signum() < 0 ? "Faltaram " + Money.format(dif.negate()) : "Sobraram " + Money.format(dif);
    }

    private void mostrarResumo(CashRegister caixa) {
        if (caixa == null) {
            labelResumo.setText("Selecione um caixa para ver o resumo.");
            return;
        }
        CashSummary r = service.summarize(caixa);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Caixa de %s até %s%n%n", DATA_HORA.format(caixa.getAbertoEm()), DATA_HORA.format(caixa.getFechadoEm())));
        sb.append(linha("Troco inicial", r.getValorInicial()));
        sb.append(linha("+ Vendas em dinheiro", r.getVendas(PaymentMethod.DINHEIRO)));
        sb.append(linha("+ Fiado recebido em dinheiro", r.getRecebimentosFiado(PaymentMethod.DINHEIRO)));
        sb.append(linha("+ Reforços", r.getReforcos()));
        sb.append(linha("- Sangrias", r.getSangrias()));
        sb.append(linha("= Devia ter na gaveta", r.getDinheiroEsperado()));
        sb.append(String.format("%n"));
        sb.append(linha("PIX", r.getTotalPix()));
        sb.append(linha("Cartão", r.getTotalCartao()));
        sb.append(linha("Vendido no fiado", r.getVendidoNoFiado()));
        sb.append(linha("Total vendido", r.getTotalVendido()));
        if (caixa.getObservacao() != null) {
            sb.append(String.format("%nObservação: %s", caixa.getObservacao()));
        }
        labelResumo.setText(sb.toString());
    }

    private static String linha(String rotulo, BigDecimal valor) {
        return String.format("%-32s %14s%n", rotulo, Money.format(valor));
    }

    private static void formatarData(TableColumn<CashRegister, LocalDateTime> coluna) {
        coluna.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DATA_HORA.format(item));
            }
        });
    }
}
