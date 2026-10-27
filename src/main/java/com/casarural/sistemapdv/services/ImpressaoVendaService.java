package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.impressao.ComprovanteVenda;
import com.casarural.sistemapdv.services.impressao.ImpressoraTermica;
import com.casarural.sistemapdv.services.impressao.PreviaTerminal;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImpressaoVendaService {
    @FunctionalInterface
    public interface Destino { void enviar(ComprovanteVenda comprovante) throws Exception; }

    // Uma fila preserva a ordem das vendas. A thread termina após ficar ociosa e não abandona
    // um envio em andamento quando a janela do sistema é fechada.
    private static final Executor FILA = new ThreadPoolExecutor(0, 1, 2, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), tarefa -> new Thread(tarefa, "impressao-comprovantes"));
    private final Destino destino;
    private final Executor executor;

    public ImpressaoVendaService() {
        this(new DestinoConfigurado(), FILA);
    }

    public ImpressaoVendaService(Destino destino, Executor executor) {
        this.destino = destino;
        this.executor = executor;
    }

    public CompletableFuture<Void> emitir(Order venda, BigDecimal troco,
                                           Map<PaymentMethod, BigDecimal> devolucoes) {
        // Captura tudo na thread do carrinho, antes de ele ser limpo para a próxima venda.
        ComprovanteVenda comprovante = ComprovanteVenda.gerar(venda, troco, devolucoes);
        return CompletableFuture.runAsync(() -> {
            try {
                destino.enviar(comprovante);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public static String modo(String propriedade, String ambiente) {
        String valor = propriedade != null ? propriedade : ambiente;
        if (valor == null || valor.isBlank()) return "impressora";
        if (valor.equalsIgnoreCase("previa")) return "previa";
        if (valor.equalsIgnoreCase("impressora")) return "impressora";
        throw new IllegalArgumentException("Modo de impressão inválido: use 'previa' ou 'impressora'.");
    }

    private static final class DestinoConfigurado implements Destino {
        private final PreviaTerminal previa = new PreviaTerminal();

        @Override
        public synchronized void enviar(ComprovanteVenda comprovante) throws Exception {
            String modo = modo(System.getProperty("pdv.impressao.modo"), System.getenv("PDV_IMPRESSAO_MODO"));
            if (modo.equals("previa")) {
                previa.enviar(comprovante);
            } else {
                String nome = System.getProperty("pdv.impressora.nome", "ELGIN i7(USB)");
                new ImpressoraTermica(nome).enviar(comprovante);
            }
        }
    }
}
