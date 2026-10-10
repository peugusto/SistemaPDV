package com.casarural.sistemapdv;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.util.Alerts;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Objects;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String MENSAGEM_ERRO_INESPERADO =
            "Ocorreu um erro inesperado e a última operação pode não ter sido concluída.\n\n"
            + "Confira se ela foi registrada. Se o erro continuar, entre em contato com o responsável pelo sistema.";

    @Override
    public void start(Stage stage) throws Exception {
        instalarTratamentoDeErros();
        log.info("Sistema iniciado | Java {} | {} | computador {} | usuário do Windows {}",
                System.getProperty("java.version"), System.getProperty("os.name"),
                System.getenv("COMPUTERNAME"), System.getProperty("user.name"));

        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/casarural/sistemapdv/view/main-view.fxml"));

        // Atualiza a estrutura do banco antes de qualquer tela. Se falhar, o sistema não abre:
        // usar um banco desatualizado ou atualizado pela metade pode gravar dados errados.
        try {
            DB.migrate();
        } catch (DbException e) {
            // showAndWait: o aviso precisa ficar na tela até o usuário ler, antes de o sistema fechar
            Alerts.showAlertAndWait("Banco de Dados", "O sistema não pode ser aberto", e.getMessage(), Alert.AlertType.ERROR);
            log.error("Sistema fechado na abertura: banco não pôde ser preparado");
            Platform.exit();
            return;
        }

        try (Connection conn = DB.getConnection()) {
            log.info("Banco de dados conectado com sucesso");
        } catch (DbException e) {
            Alerts.showAlert("Banco de Dados", "Sem conexão com o banco", e.getMessage(), Alert.AlertType.WARNING);
        }

        // Os atalhos F1-F5 são registrados pelo próprio MainController (configurarAtalhos)
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/casarural/sistemapdv/css/style.css")).toExternalForm());

        stage.setTitle("Casa Rural");
        stage.setMaximized(true);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Qualquer exceção que nenhum código tratou (num clique, numa tela, numa thread) vem parar aqui:
     * o erro completo vai para o log e o usuário vê uma mensagem simples, em vez de nada acontecer.
     */
    private static void instalarTratamentoDeErros() {
        Thread.UncaughtExceptionHandler handler = (thread, erro) -> {
            log.error("Erro não tratado na thread {}", thread.getName(), erro);
            Runnable aviso = () -> {
                try {
                    Alerts.showAlert("Erro", "Algo deu errado", MENSAGEM_ERRO_INESPERADO, Alert.AlertType.ERROR);
                } catch (Exception falhaNoAviso) {
                    log.error("Não foi possível mostrar o aviso de erro", falhaNoAviso);
                }
            };
            if (Platform.isFxApplicationThread()) {
                aviso.run();
            } else {
                Platform.runLater(aviso);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);   // threads criadas depois
        Thread.currentThread().setUncaughtExceptionHandler(handler);   // a thread da tela (JavaFX)
    }

    @Override
    public void stop() {
        DB.closeDataSource();
        log.info("Sistema encerrado");
    }

    public static void main(String[] args) {
        launch();
    }
}
