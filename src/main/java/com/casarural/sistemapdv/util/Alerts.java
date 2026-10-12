package com.casarural.sistemapdv.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;

public class Alerts {

    private static final Logger log = LoggerFactory.getLogger(Alerts.class);

    public static void showAlert(String title, String header, String content, AlertType type) {
        registrar(title, header, content, type);
        criar(title, header, content, type).show();
    }

    /** Igual ao showAlert, mas só continua depois que o usuário clicar em OK. */
    public static void showAlertAndWait(String title, String header, String content, AlertType type) {
        registrar(title, header, content, type);
        criar(title, header, content, type).showAndWait();
    }

    private static Alert criar(String title, String header, String content, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Tema.aplicar(alert.getDialogPane());
        return alert;
    }

    /**
     * Todo erro ou aviso mostrado ao usuário fica registrado no log: quando seus pais disserem
     * "apareceu uma mensagem", dá para ver exatamente qual foi e a que horas.
     */
    private static void registrar(String title, String header, String content, AlertType type) {
        String texto = (header != null ? header + " - " : "") + content;
        if (type == AlertType.ERROR) {
            log.error("Mensagem de erro mostrada ao usuário [{}]: {}", title, texto);
        } else if (type == AlertType.WARNING) {
            log.warn("Aviso mostrado ao usuário [{}]: {}", title, texto);
        }
    }

    /**
     * Aviso com botões próprios. O primeiro botão (textoVoltar) apenas fecha o aviso.
     * Retorna o índice da opção escolhida em "opcoes", ou -1 se a pessoa voltou ou fechou a janela.
     */
    public static int showChoice(String title, String header, String content, AlertType type,
                                 String textoVoltar, String... opcoes) {
        registrar(title, header, content, type);
        Alert alert = criar(title, header, content, type);
        ButtonType voltar = new ButtonType(textoVoltar, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType[] botoes = new ButtonType[opcoes.length];
        for (int i = 0; i < opcoes.length; i++) {
            botoes[i] = new ButtonType(opcoes[i], ButtonBar.ButtonData.OTHER);
        }
        alert.getButtonTypes().setAll(voltar);
        alert.getButtonTypes().addAll(botoes);

        Optional<ButtonType> escolha = alert.showAndWait();
        if (escolha.isEmpty()) return -1;
        for (int i = 0; i < botoes.length; i++) {
            if (escolha.get() == botoes[i]) return i;
        }
        return -1;
    }

    public static boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Tema.aplicar(alert.getDialogPane());

        ButtonType btnSim = new ButtonType("Sim", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnNao = new ButtonType("Não", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSim, btnNao);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == btnSim;
    }

    /**
     * Pede um valor em reais (ex.: o valor mostrado na balança).
     * Volta vazio se a pessoa cancelar; valor zero ou inválido pede de novo.
     */
    public static Optional<BigDecimal> pedirValor(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        dialog.getEditor().setPromptText("0,00");
        dialog.getDialogPane().getButtonTypes().setAll(
                new ButtonType("Lançar", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        Constraints.setTextFieldMoney(dialog.getEditor());
        Tema.aplicar(dialog.getDialogPane());

        while (true) {
            Optional<String> texto = dialog.showAndWait();
            if (texto.isEmpty()) return Optional.empty();
            try {
                BigDecimal valor = Money.parse(texto.get());
                if (Money.isPositive(valor)) return Optional.of(valor);
            } catch (NumberFormatException e) {
                // cai no aviso abaixo
            }
            showAlertAndWait(title, null, "Digite o valor em reais, por exemplo 12,40.", AlertType.WARNING);
            dialog.getEditor().selectAll();
        }
    }

    /**
     * Pede um texto obrigatório (ex.: o motivo de um cancelamento), com uma explicação acima do campo.
     * Volta vazio se a pessoa cancelar.
     */
    public static Optional<String> pedirTexto(String title, String header, String explicacao, String rotuloCampo,
                                              String textoConfirmar) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        javafx.scene.control.TextField campo = new javafx.scene.control.TextField();
        campo.setPromptText(rotuloCampo);
        Constraints.setTextFieldMaxLength(campo, 255);
        javafx.scene.control.Label texto = new javafx.scene.control.Label(explicacao);
        texto.setWrapText(true);
        texto.setMaxWidth(420);
        javafx.scene.layout.VBox conteudo = new javafx.scene.layout.VBox(12, texto, campo);
        dialog.getDialogPane().setContent(conteudo);
        ButtonType ok = new ButtonType(textoConfirmar, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ok, new ButtonType("Voltar", ButtonBar.ButtonData.CANCEL_CLOSE));
        // Sem motivo não deixa confirmar
        javafx.scene.Node botaoOk = dialog.getDialogPane().lookupButton(ok);
        botaoOk.setDisable(true);
        campo.textProperty().addListener((obs, a, b) -> botaoOk.setDisable(b == null || b.isBlank()));
        dialog.setResultConverter(b -> b == ok ? campo.getText().trim() : null);
        Tema.aplicar(dialog.getDialogPane());
        javafx.application.Platform.runLater(campo::requestFocus);
        return dialog.showAndWait();
    }
}
