package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.services.ImpressaoVendaService.Destino;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Chamada nativa isolada: cancelar somente uma thread não interrompe um driver travado. */
public final class DestinoProcesso implements Destino {
    @FunctionalInterface interface Iniciar { Process criar() throws IOException; }
    private static final Semaphore EXCLUSIVO = new Semaphore(1);
    private static final AtomicReference<Process> ANTERIOR = new AtomicReference<>();
    private final Iniciar iniciar;
    private final long prazoMs;

    public DestinoProcesso(String impressora) {
        this(() -> comando(impressora).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start(), prazoConfigurado());
    }
    DestinoProcesso(Iniciar iniciar,long prazoMs) { this.iniciar=iniciar;this.prazoMs=prazoMs; }

    private static long prazoConfigurado() {
        long segundos = Long.getLong("pdv.impressao.prazoSegundos",30);
        if (segundos < 1 || segundos > 120) throw new IllegalArgumentException("Prazo da impressão deve ficar entre 1 e 120 segundos.");
        return segundos * 1000;
    }
    static ProcessBuilder comando(String impressora) {
        List<String> comando = new ArrayList<>();
        comando.add(Path.of(System.getProperty("java.home"),"bin","java.exe").toString());
        String modulos = System.getProperty("jdk.module.path");
        if (DestinoProcesso.class.getModule().isNamed()) {
            if (modulos == null || modulos.isBlank()) throw new IllegalStateException("Caminho do processo de impressão não encontrado.");
            Collections.addAll(comando,"--module-path",modulos,"--add-modules=ALL-MODULE-PATH","-m",
                    DestinoProcesso.class.getModule().getName()+"/"+ImprimirProcesso.class.getName());
        } else Collections.addAll(comando,"-cp",System.getProperty("java.class.path"),ImprimirProcesso.class.getName());
        comando.add(impressora);
        return new ProcessBuilder(comando);
    }

    @Override public void enviar(Comprovante documento) throws Exception {
        if (!EXCLUSIVO.tryAcquire()) throw new IOException("Há outro envio em andamento. Aguarde antes de solicitar a segunda via.");
        Process processo = null;
        try {
            Process antigo = ANTERIOR.get();
            if (antigo != null && antigo.isAlive()) throw new IOException("O processo anterior não encerrou. Confira a impressora antes de tentar novamente.");
            // Valida o tamanho antes de iniciar qualquer processo ou envio físico.
            java.io.ByteArrayOutputStream dados = new java.io.ByteArrayOutputStream();ImprimirProcesso.gravar(dados,documento);
            processo = iniciar.criar();ANTERIOR.set(processo);
            Process atual = processo;
            CompletableFuture<Void> escrita = new CompletableFuture<>();
            Thread entrada = new Thread(() -> {
                try (java.io.OutputStream out = atual.getOutputStream()) { dados.writeTo(out);escrita.complete(null); }
                catch (Exception e) { escrita.completeExceptionally(e); }
            },"documento-impressao");entrada.setDaemon(true);entrada.start();
            if (!processo.waitFor(prazoMs,TimeUnit.MILLISECONDS)) throw new EnvioImpressaoIncertoException();
            if (processo.exitValue() != 0 || !escrita.isDone() || escrita.isCompletedExceptionally()) throw new EnvioImpressaoIncertoException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();throw new EnvioImpressaoIncertoException();
        } finally {
            if (processo != null && processo.isAlive()) {
                processo.destroyForcibly();
                try { processo.waitFor(2,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            EXCLUSIVO.release();
        }
    }
}
