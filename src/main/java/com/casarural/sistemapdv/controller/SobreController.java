package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.services.SobreService;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.Tema;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/** Ajuda > Sobre. */
public class SobreController {

    private static final Logger log = LoggerFactory.getLogger(SobreController.class);

    @FXML private Label lblNome, lblVersao, lblCopiado;
    @FXML private VBox colunaEsquerda, colunaDireita;
    @FXML private javafx.scene.control.ScrollPane rolagem;
    @FXML private javafx.scene.layout.HBox colunas;

    private SobreService service;
    private Map<String, List<String[]>> dados;

    public static void abrir(Window dono) {
        try {
            FXMLLoader loader = new FXMLLoader(SobreController.class.getResource("/com/casarural/sistemapdv/view/Sobre.fxml"));
            Parent root = loader.load();
            SobreController controller = loader.getController();
            controller.setService(new SobreService());

            Stage stage = new Stage();
            stage.setTitle("Sobre o SistemaPDV");
            if (dono != null) stage.initOwner(dono);
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            Tema.aplicar(scene);
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) stage.close();
            });
            stage.setScene(scene);
            // Nunca maior que a área útil da tela (sem a barra de tarefas); o meio rola se precisar
            Rectangle2D tela = Screen.getPrimary().getVisualBounds();
            stage.setMaxHeight(tela.getHeight());
            stage.setMaxWidth(tela.getWidth());
            stage.setOnShown(e -> {
                stage.sizeToScene(); // mede de novo, já com os cartões montados
                if (stage.getHeight() > tela.getHeight()) stage.setHeight(tela.getHeight());
                if (stage.getWidth() > tela.getWidth()) stage.setWidth(tela.getWidth());
                stage.centerOnScreen();
            });
            stage.showAndWait();
        } catch (Exception e) {
            log.error("Erro ao abrir Sobre", e);
            Alerts.showAlert("Sobre", "Não foi possível abrir", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void setService(SobreService service) {
        this.service = service;
        lblNome.setText(service.valor("sistema.nome") + " · " + service.valor("sistema.loja"));
        lblVersao.setText("Versão " + service.versao() + "  ·  ponto de venda");
        dados = service.grupos();

        // Metade dos cartões em cada coluna (Sistema e Banco | Tecnologias e Arquivos)
        colunaEsquerda.getChildren().clear();
        colunaDireita.getChildren().clear();
        int i = 0;
        int metade = (dados.size() + 1) / 2;
        for (Map.Entry<String, List<String[]>> g : dados.entrySet()) {
            (i++ < metade ? colunaEsquerda : colunaDireita).getChildren().add(cartao(g.getKey(), g.getValue()));
        }
        // A rolagem pede a altura inteira dos cartões; só rola de verdade se a tela for menor
        colunas.layoutBoundsProperty().addListener((obs, antes, agora) ->
                rolagem.setPrefViewportHeight(agora.getHeight() + 4)); // folga de arredondamento
    }

    private static VBox cartao(String titulo, List<String[]> linhas) {
        Label lTitulo = new Label(titulo);
        lTitulo.getStyleClass().add("titulo-cartao");

        GridPane grade = new GridPane();
        grade.setHgap(16);
        grade.setVgap(6);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(150);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grade.getColumnConstraints().addAll(c1, c2);
        for (int i = 0; i < linhas.size(); i++) {
            Label rotulo = new Label(linhas.get(i)[0]);
            rotulo.getStyleClass().add("texto-suave");
            Label valor = new Label(linhas.get(i)[1]);
            valor.setWrapText(true);
            valor.getStyleClass().add("texto");
            grade.add(rotulo, 0, i);
            grade.add(valor, 1, i);
        }

        VBox cartao = new VBox(10, lTitulo, grade);
        cartao.getStyleClass().add("cartao");
        return cartao;
    }

    @FXML
    public void onCopiar() {
        ClipboardContent conteudo = new ClipboardContent();
        conteudo.putString(service.texto(dados));
        Clipboard.getSystemClipboard().setContent(conteudo);
        lblCopiado.setText("Copiado. Cole numa mensagem para quem dá suporte.");
    }

    @FXML
    public void onFechar() {
        ((Stage) colunaEsquerda.getScene().getWindow()).close();
    }
}
