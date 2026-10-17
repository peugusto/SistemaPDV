package com.casarural.sistemapdv;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.util.Alerts;
import com.casarural.sistemapdv.util.InstanciaUnica;
import com.casarural.sistemapdv.util.Tema;
import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.services.PreferenciasTelaService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String MENSAGEM_ERRO_INESPERADO =
            "Ocorreu um erro inesperado e a última operação pode não ter sido concluída.\n\n"
            + "Confira se ela foi registrada. Se o erro continuar, entre em contato com o responsável pelo sistema.";

    /** Ao ligar o PC, o MySQL pode demorar para subir: espera até este tempo antes de desistir. */
    private static final long ESPERA_BANCO_MS = 45_000;
    private static final long INTERVALO_TENTATIVA_MS = 3_000;

    @Override
    public void start(Stage stage) {
        instalarTratamentoDeErros();
        log.info("Sistema iniciado | Java {} | {} | computador {} | usuário do Windows {}",
                System.getProperty("java.version"), System.getProperty("os.name"),
                System.getenv("COMPUTERNAME"), System.getProperty("user.name"));

        // Duplo clique repetido no ícone: não abre um segundo sistema
        if (!InstanciaUnica.obter()) {
            log.info("Sistema já estava aberto; esta segunda abertura foi cancelada");
            Alerts.showAlertAndWait("Casa Rural", "O sistema já está aberto",
                    "Procure a janela do sistema na barra de tarefas, embaixo na tela.", Alert.AlertType.INFORMATION);
            Platform.exit();
            return;
        }

        // Janelinha de espera enquanto o banco é preparado (fora da thread da tela, para ela não travar)
        Stage espera = new Stage(StageStyle.UNDECORATED);
        Label aviso = new Label("Preparando o sistema…");
        aviso.getStyleClass().add("valor-grande");
        Label detalhe = new Label("Aguarde um instante.");
        detalhe.getStyleClass().add("texto-suave");
        VBox caixa = new VBox(8, aviso, detalhe);
        caixa.getStyleClass().add("cartao");
        caixa.setPadding(new Insets(28, 36, 28, 36));
        Scene cenaEspera = new Scene(caixa);
        Tema.aplicar(cenaEspera);
        espera.setScene(cenaEspera);
        espera.show();

        Thread abertura = new Thread(() -> {
            try {
                prepararBanco(detalhe);
                Platform.runLater(() -> {
                    try {
                        abrirTelaPrincipal(stage);
                    } catch (Exception e) {
                        log.error("Erro ao abrir a tela principal", e);
                        Alerts.showAlertAndWait("Casa Rural", "O sistema não pode ser aberto", MENSAGEM_ERRO_INESPERADO, Alert.AlertType.ERROR);
                        Platform.exit();
                    } finally {
                        espera.close();
                    }
                });
            } catch (DbException e) {
                Platform.runLater(() -> {
                    // showAndWait: o aviso precisa ficar na tela até o usuário ler, antes de o sistema fechar
                    Alerts.showAlertAndWait("Banco de Dados", "O sistema não pode ser aberto", e.getMessage(), Alert.AlertType.ERROR);
                    log.error("Sistema fechado na abertura: banco não pôde ser preparado");
                    espera.close();
                    Platform.exit();
                });
            }
        }, "abertura");
        abertura.setDaemon(true);
        abertura.start();
    }

    /**
     * Atualiza a estrutura do banco (Flyway) antes de qualquer tela. Se falhar, o sistema não abre:
     * usar um banco desatualizado ou atualizado pela metade pode gravar dados errados.
     * Sem conexão (MySQL ainda iniciando), tenta de novo por até ESPERA_BANCO_MS.
     */
    private static void prepararBanco(Label detalhe) {
        long limite = System.currentTimeMillis() + ESPERA_BANCO_MS;
        int tentativa = 1;
        while (true) {
            try {
                DB.migrate();
                return;
            } catch (DbException e) {
                boolean semConexao = DB.MENSAGEM_SEM_CONEXAO.equals(e.getMessage());
                if (!semConexao || System.currentTimeMillis() >= limite) throw e;
                tentativa++;
                int n = tentativa;
                log.info("Banco ainda não respondeu; tentando de novo (tentativa {})", n);
                Platform.runLater(() -> detalhe.setText("Esperando o banco de dados ligar… (tentativa " + n + ")"));
                try {
                    Thread.sleep(INTERVALO_TENTATIVA_MS);
                } catch (InterruptedException interrompido) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private void abrirTelaPrincipal(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/casarural/sistemapdv/view/main-view.fxml"));

        try (Connection conn = DB.getConnection()) {
            log.info("Banco de dados conectado com sucesso");
        } catch (DbException e) {
            Alerts.showAlert("Banco de Dados", "Sem conexão com o banco", e.getMessage(), Alert.AlertType.WARNING);
        }

        // Acessibilidade: tamanho da letra e cores escolhidos da última vez (tabela configuracao)
        PreferenciasTelaService preferencias = new PreferenciasTelaService(DaoFactory.createConfiguracaoDAO());
        preferencias.carregar();
        Tema.aoMudar(preferencias::salvar);

        // Os atalhos F1-F5 são registrados pelo próprio MainController (configurarAtalhos)
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        Tema.aplicar(scene);

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
