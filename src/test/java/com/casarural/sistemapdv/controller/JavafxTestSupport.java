package com.casarural.sistemapdv.controller;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Um toolkit por execução JUnit; cada teste fecha suas próprias janelas. */
public final class JavafxTestSupport implements BeforeAllCallback {
    public void beforeAll(ExtensionContext context) throws Exception {
        assumeFalse(Boolean.getBoolean("java.awt.headless") ||
                (System.getProperty("os.name", "").startsWith("Linux") && System.getenv("DISPLAY") == null),
                "Requer ambiente gráfico JavaFX.");
        synchronized (JavafxTestSupport.class) {
            ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.create(JavafxTestSupport.class));
            if (store.get("runtime") == null) store.put("runtime", new RuntimeFx());
        }
    }
    private static final class RuntimeFx implements ExtensionContext.Store.CloseableResource {
        RuntimeFx() throws Exception {
            CountDownLatch ready = new CountDownLatch(1);
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX não iniciou.");
        }
        public void close() { Platform.runLater(() -> { fecharJanelas(); Platform.exit(); }); }
    }
    @FunctionalInterface public interface AcaoFx { void executar() throws Exception; }
    public static void executar(AcaoFx acao) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { try { acao.executar(); } finally { fecharJanelas(); } return null; });
        Platform.runLater(task);
        try { task.get(20, TimeUnit.SECONDS); }
        catch (ExecutionException e) {
            if(e.getCause() instanceof Error) throw (Error)e.getCause();
            if(e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw e;
        }
    }
    public static void fecharJanelas() {
        for(Window w : new ArrayList<>(Window.getWindows())) if(w instanceof Stage) ((Stage)w).close();
    }
}
