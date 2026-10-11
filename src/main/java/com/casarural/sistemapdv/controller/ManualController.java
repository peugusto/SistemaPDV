package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Manual;
import com.casarural.sistemapdv.util.Manual.Bloco;
import com.casarural.sistemapdv.util.Manual.Secao;
import com.casarural.sistemapdv.util.Tema;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/** Ajuda > Manual e Ajuda > Problemas e soluções. */
public class ManualController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ManualController.class);
    public static final String PROBLEMAS = "Problemas e soluções";

    @FXML private TextField txtBusca;
    @FXML private ListView<Secao> listaAssuntos;
    @FXML private ScrollPane rolagem;
    @FXML private VBox conteudo;

    private FilteredList<Secao> secoes;

    /** Abre o manual; assuntoInicial = título do assunto a mostrar primeiro (null = o primeiro). */
    public static void abrir(Window dono, String assuntoInicial) {
        try {
            FXMLLoader loader = new FXMLLoader(ManualController.class.getResource("/com/casarural/sistemapdv/view/Manual.fxml"));
            Parent root = loader.load();
            ManualController controller = loader.getController();
            controller.mostrarAssunto(assuntoInicial);

            Stage stage = new Stage();
            stage.setTitle("Manual - Casa Rural");
            if (dono != null) stage.initOwner(dono);
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            Tema.aplicar(scene);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) stage.close();
            });
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Erro ao abrir o manual", e);
            Alerts.showAlert("Manual", "Não foi possível abrir o manual", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        secoes = new FilteredList<>(FXCollections.observableArrayList(Manual.carregar()), s -> true);
        listaAssuntos.setItems(secoes);
        listaAssuntos.getSelectionModel().selectedItemProperty().addListener((obs, antes, agora) -> {
            if (agora != null) desenhar(agora);
        });
        txtBusca.textProperty().addListener((obs, antes, agora) -> {
            secoes.setPredicate(s -> s.contem(agora));
            if (!secoes.isEmpty() && listaAssuntos.getSelectionModel().getSelectedItem() == null) {
                listaAssuntos.getSelectionModel().selectFirst();
            }
            if (secoes.isEmpty()) {
                conteudo.getChildren().setAll(texto("Nada encontrado para \"" + agora + "\".", "manual-paragrafo"));
            }
        });
    }

    public void mostrarAssunto(String titulo) {
        Secao alvo = secoes.stream().filter(s -> s.getTitulo().equals(titulo)).findFirst().orElse(null);
        if (alvo != null) listaAssuntos.getSelectionModel().select(alvo);
        else listaAssuntos.getSelectionModel().selectFirst();
        listaAssuntos.scrollTo(listaAssuntos.getSelectionModel().getSelectedIndex());
    }

    private void desenhar(Secao secao) {
        conteudo.getChildren().clear();
        conteudo.getChildren().add(texto(secao.getTitulo(), "manual-titulo"));
        for (Bloco b : secao.getBlocos()) {
            conteudo.getChildren().add(bloco(b));
        }
        rolagem.setVvalue(0);
    }

    private static Node bloco(Bloco b) {
        switch (b.getTipo()) {
            case SUBTITULO:
                return texto(b.getTexto(), "manual-subtitulo");
            case ITEM:
                return linha(texto("•", "manual-marcador"), texto(b.getTexto(), "manual-paragrafo"));
            case PASSO:
                Label numero = texto(String.valueOf(b.getNumero()), "manual-numero");
                numero.setMinSize(26, 26);
                numero.setAlignment(Pos.CENTER);
                return linha(numero, texto(b.getTexto(), "manual-paragrafo"));
            case AVISO:
                return caixa(b.getTexto(), "manual-aviso");
            case MENSAGEM:
                return caixa(b.getTexto(), "manual-mensagem");
            default:
                return texto(b.getTexto(), "manual-paragrafo");
        }
    }

    private static Label texto(String t, String classe) {
        Label l = new Label(t);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add(classe);
        return l;
    }

    private static HBox linha(Label marcador, Label texto) {
        HBox h = new HBox(10, marcador, texto);
        h.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(texto, Priority.ALWAYS);
        return h;
    }

    private static Label caixa(String t, String classe) {
        Label l = texto(t, classe);
        l.setMinHeight(Label.USE_PREF_SIZE);
        return l;
    }
}
