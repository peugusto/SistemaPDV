package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.services.AcessoRelatoriosService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.SenhaPin;
import com.casarural.sistemapdv.util.Tema;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Pede (ou cria, na primeira vez) o PIN da loja: relatórios, cancelamentos, estornos e descontos. */
public final class PinDialogo {

    private static final Logger log = LoggerFactory.getLogger(PinDialogo.class);
    private static final int TENTATIVAS = 3;

    private PinDialogo() {}

    /** true se a pessoa pode ver os relatórios. */
    public static boolean liberar(AcessoRelatoriosService acesso, Window dono) {
        return liberar(acesso, dono, "Relatórios", "ver os relatórios");
    }

    /**
     * Pede o PIN da loja antes de uma ação protegida.
     * @param titulo título da janela (ex.: "Cancelar venda")
     * @param acao   o que vai ser feito, para a frase "Digite o PIN para ..." (ex.: "cancelar a venda #12")
     */
    public static boolean liberar(AcessoRelatoriosService acesso, Window dono, String titulo, String acao) {
        if (!acesso.temPin()) return criarPin(acesso, dono);

        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            String aviso = tentativa == 1 ? null : "PIN errado. Tentativa " + tentativa + " de " + TENTATIVAS + ".";
            Optional<String> pin = pedir(dono, titulo, "Digite o PIN para " + acao, aviso);
            if (pin.isEmpty()) return false;
            if (acesso.confere(pin.get())) {
                log.info("PIN conferido para {}", acao);
                return true;
            }
        }
        log.warn("PIN errado {} vezes ao tentar {}", TENTATIVAS, acao);
        Alerts.showAlertAndWait(titulo, "PIN errado " + TENTATIVAS + " vezes",
                "Nada foi feito.", Alert.AlertType.WARNING);
        return false;
    }

    /** Primeira vez: cria o PIN digitando duas vezes. */
    public static boolean criarPin(AcessoRelatoriosService acesso, Window dono) {
        String aviso = "O PIN da loja protege os relatórios (faturamento), os cancelamentos, os estornos "
                + "e os descontos. Crie um PIN de 4 a 6 números e guarde: ele será pedido toda vez.";
        while (true) {
            Optional<String> pin = pedir(dono, "Criar PIN", "Crie o PIN da loja", aviso);
            if (pin.isEmpty()) return false;
            if (!SenhaPin.formatoValido(pin.get())) {
                aviso = "Use de 4 a 6 números.";
                continue;
            }
            Optional<String> confirmacao = pedir(dono, "Criar PIN", "Digite o PIN de novo", null);
            if (confirmacao.isEmpty()) return false;
            if (!pin.get().equals(confirmacao.get())) {
                aviso = "Os dois PINs ficaram diferentes. Comece de novo.";
                continue;
            }
            acesso.definirPin(pin.get());
            log.info("PIN dos relatórios criado/alterado");
            return true;
        }
    }

    private static Optional<String> pedir(Window dono, String titulo, String cabecalho, String aviso) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.setHeaderText(cabecalho);
        if (dono != null) dialog.initOwner(dono);

        PasswordField campo = new PasswordField();
        campo.setPromptText("PIN");
        campo.getStyleClass().add("campo-grande");
        // só números, no máximo 6
        campo.textProperty().addListener((obs, antes, agora) -> {
            if (!agora.matches("\\d{0," + SenhaPin.TAMANHO_MAXIMO + "}")) campo.setText(antes);
        });

        VBox conteudo = new VBox(10);
        if (aviso != null) {
            Label lAviso = new Label(aviso);
            lAviso.setWrapText(true);
            lAviso.setMaxWidth(320);
            conteudo.getChildren().add(lAviso);
        }
        conteudo.getChildren().add(campo);
        dialog.getDialogPane().setContent(conteudo);

        ButtonType entrar = new ButtonType("Entrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(entrar, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setResultConverter(b -> b == entrar ? campo.getText() : null);
        Tema.aplicar(dialog.getDialogPane());
        Platform.runLater(campo::requestFocus);
        return dialog.showAndWait();
    }
}
