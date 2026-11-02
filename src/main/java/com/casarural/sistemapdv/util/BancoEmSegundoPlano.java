package com.casarural.sistemapdv.util;

import com.casarural.sistemapdv.db.DbException;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Mantém o contrato dos DAOs e a tela responsiva, sem liberar novas ações durante uma gravação. */
public final class BancoEmSegundoPlano {
    private static final Executor EXECUTOR = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r,"operacao-banco"); t.setDaemon(true); return t; });
    private static boolean aguardando;
    private BancoEmSegundoPlano() {}

    public static <T> T proteger(Class<T> tipo, T dao) {
        return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (p,m,a) -> {
            if (m.getDeclaringClass() == Object.class) return m.invoke(dao,a);
            return executar(() -> {
                try { return m.invoke(dao,a); }
                catch (InvocationTargetException e) {
                    if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
                    throw (Error) e.getCause();
                }
            });
        }));
    }

    public static <T> T executar(Callable<T> operacao) {
        if (!Platform.isFxApplicationThread()) {
            try { return operacao.call(); } catch (Throwable e) { return propagar(e); }
        }
        if (aguardando) throw new DbException("Aguarde a operação atual terminar antes de iniciar outra.");
        Stage espera = new Stage(StageStyle.UNDECORATED);
        espera.initModality(Modality.APPLICATION_MODAL);
        Map<Node,Boolean> anteriores = new IdentityHashMap<>();
        for (Window w : new ArrayList<>(Window.getWindows())) if (w.isShowing() && w.getScene() != null) {
            if (w.isFocused()) espera.initOwner(w);
            Node raiz = w.getScene().getRoot(); anteriores.put(raiz,raiz.isDisable());
        }
        ProgressIndicator indicador = new ProgressIndicator();indicador.setPrefSize(28,28);
        HBox conteudo = new HBox(12,indicador,new Label("Aguarde a conclusão da operação…"));conteudo.setPadding(new Insets(24));
        Scene cena = new Scene(conteudo);Tema.aplicar(cena);espera.setScene(cena);
        espera.setOnCloseRequest(e -> e.consume());
        // A maioria das consultas locais termina antes de aparecer qualquer janela de espera.
        espera.setOpacity(0);
        PauseTransition mostrar = new PauseTransition(Duration.millis(180));mostrar.setOnFinished(e -> espera.setOpacity(1));
        CompletableFuture<T> resultado = new CompletableFuture<>();
        espera.setOnShown(e -> {
            mostrar.play();
            try {
                EXECUTOR.execute(() -> {
                    try { resultado.complete(operacao.call()); }
                    catch (Throwable erro) { resultado.completeExceptionally(erro); }
                    finally { Platform.runLater(espera::hide); }
                });
            } catch (RejectedExecutionException erro) {
                resultado.completeExceptionally(new DbException("Há muitas operações em andamento. Aguarde e tente novamente."));
                Platform.runLater(espera::hide);
            }
        });
        aguardando = true;
        try {
            anteriores.keySet().forEach(n -> n.setDisable(true));
            // showAndWait processa os eventos JavaFX; o JDBC roda somente no executor.
            // A operação só é submetida em onShown: falha ao abrir o diálogo não inicia uma escrita.
            espera.showAndWait();
            return resultado.join();
        } catch (CompletionException e) { return propagar(e.getCause()); }
        finally {
            mostrar.stop();espera.hide();anteriores.forEach(Node::setDisable);aguardando = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T,E extends Throwable> T propagar(Throwable e) throws E { throw (E)e; }
}
