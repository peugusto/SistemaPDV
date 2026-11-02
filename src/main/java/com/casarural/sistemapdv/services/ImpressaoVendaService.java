package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.dao.RelatorioImpressaoDao;
import com.casarural.sistemapdv.model.dao.ArquivoComprovanteDao;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado.Tipo;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.services.impressao.SegundaVia;
import com.casarural.sistemapdv.services.impressao.ComprovanteDivida;
import com.casarural.sistemapdv.services.impressao.ComprovanteFechamento;
import java.util.function.Supplier;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.impressao.ComprovanteVenda;
import com.casarural.sistemapdv.services.impressao.Comprovante;
import com.casarural.sistemapdv.services.impressao.ComprovanteFiado;
import com.casarural.sistemapdv.services.impressao.PreviaTerminal;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImpressaoVendaService {
    @FunctionalInterface
    public interface Destino { void enviar(Comprovante comprovante) throws Exception; }

    private static final ThreadPoolExecutor FILA = criarFila(16);
    static ThreadPoolExecutor criarFila(int capacidade) {
        ThreadPoolExecutor fila = new ThreadPoolExecutor(1,1,2,TimeUnit.SECONDS,new ArrayBlockingQueue<>(capacidade),r -> {
            Thread t=new Thread(r,"impressao-comprovantes");t.setDaemon(true);return t;
        });
        fila.allowCoreThreadTimeOut(true);return fila;
    }
    private final Destino destino;
    private final Executor executor;
    private final RelatorioImpressaoDao relatorios;
    private final ArquivoComprovanteDao arquivo;
    private final java.util.function.LongSupplier agora;

    public ImpressaoVendaService() {
        this(new DestinoConfigurado(), FILA);
    }

    public ImpressaoVendaService(Destino destino, Executor executor) {
        this(destino, executor, DaoFactory.createRelatorioImpressaoDAO());
    }
    public ImpressaoVendaService(Destino destino, Executor executor, RelatorioImpressaoDao relatorios) {
        this(destino, executor, relatorios, DaoFactory.createArquivoComprovanteDAO());
    }
    public ImpressaoVendaService(Destino destino, Executor executor, RelatorioImpressaoDao relatorios, ArquivoComprovanteDao arquivo) {
        this(destino,executor,relatorios,arquivo,System::nanoTime);
    }
    public ImpressaoVendaService(Destino destino, Executor executor, RelatorioImpressaoDao relatorios, ArquivoComprovanteDao arquivo, java.util.function.LongSupplier agora) {
        this.agora=java.util.Objects.requireNonNull(agora);
        this.arquivo = java.util.Objects.requireNonNull(arquivo);
        this.relatorios = relatorios;
        this.destino = destino;
        this.executor = executor;
    }

    public CompletableFuture<Void> emitir(Order venda, BigDecimal troco,
                                           Map<PaymentMethod, BigDecimal> devolucoes) {
        // Captura tudo na thread do carrinho, antes de ele ser limpo para a próxima venda.
        return arquivarEEnviar(Tipo.VENDA,String.valueOf(venda.getIdPedido()),ComprovanteVenda.gerar(venda, troco, devolucoes));
    }

    public CompletableFuture<Void> emitir(RecebimentoFiado recebimento) {
        return arquivarEEnviar(Tipo.FIADO,String.valueOf(recebimento.getIdRecebimento()),ComprovanteFiado.gerar(recebimento));
    }

    public CompletableFuture<Void> emitirListaCompras(java.util.List<com.casarural.sistemapdv.model.entities.RegraListaCompras.Item> itens, java.time.LocalDateTime atualizadaEm) {
        return arquivarEEnviar(Tipo.LISTA_COMPRAS,java.util.UUID.randomUUID().toString(),com.casarural.sistemapdv.services.impressao.ComprovanteListaCompras.gerar(itens, atualizadaEm));
    }

    private CompletableFuture<Void> arquivarEEnviar(Tipo tipo,String referencia,Comprovante documento) {
        ComprovanteGuardado captura=new ComprovanteGuardado(tipo,referencia,documento);
        try {
            // Guarda antes de entrar na fila do driver, inclusive quando ela estiver cheia/travada.
            Comprovante original=arquivo.guardar(captura);
            return enviar(() -> original);
        } catch (Exception erro) {
            CompletableFuture<Void> resultado=new CompletableFuture<>();resultado.completeExceptionally(erro);return resultado;
        }
    }
    public CompletableFuture<Void> reimprimirVenda(int idVenda) {
        return enviar(() -> {
            Comprovante historico=relatorios.vendaHistorica(idVenda);
            return SegundaVia.de(arquivo.buscar(Tipo.VENDA,String.valueOf(idVenda)).<Comprovante>map(d->d).orElse(historico),java.time.LocalDateTime.now());
        });
    }
    public CompletableFuture<Void> reimprimirRecebimento(int idCliente,Payment selecionado) {
        Integer id=selecionado.getIdRecebimento();
        java.time.LocalDateTime data=selecionado.getDataPagamento();PaymentMethod forma=selecionado.getMetodo();
        return enviar(() -> {
            Comprovante historico=relatorios.recebimentoHistorico(idCliente,id,data,forma);
            Comprovante original=id==null?historico:arquivo.buscar(Tipo.FIADO,String.valueOf(id)).<Comprovante>map(d->d).orElse(historico);
            return SegundaVia.de(original,java.time.LocalDateTime.now());
        });
    }
    public CompletableFuture<Void> reimprimirUltimaLista() {
        return enviar(() -> SegundaVia.de(arquivo.ultimaLista().orElseThrow(() -> new IllegalArgumentException(
                "Nenhuma lista de compras foi guardada ainda. Imprima uma lista primeiro.")),java.time.LocalDateTime.now()));
    }

    /** A leitura atualizada ocorre na fila, fora da thread da tela. */
    public CompletableFuture<Void> emitirFechamento(int idCaixa) {
        return enviar(() -> ComprovanteFechamento.gerar(relatorios.fechamentoCaixa(idCaixa)));
    }
    public CompletableFuture<Void> emitirDivida(int idCliente) {
        return enviar(() -> ComprovanteDivida.gerar(relatorios.extratoFiado(idCliente)));
    }
    private CompletableFuture<Void> enviar(Supplier<Comprovante> obter) {
        Trabalho trabalho=new Trabalho(obter);
        try { executor.execute(trabalho); }
        catch (RejectedExecutionException e) { trabalho.resultado.completeExceptionally(new java.io.IOException("Fila de impressão cheia ou encerrada. Use a segunda via depois de conferir a impressora.")); }
        return trabalho.resultado;
    }
    private final class Trabalho implements Runnable {
        final CompletableFuture<Void> resultado=new CompletableFuture<>();
        final Supplier<Comprovante> obter;final long criado=agora.getAsLong();
        Trabalho(Supplier<Comprovante> obter){this.obter=obter;}
        public void run() {
            try {
                if (agora.getAsLong()-criado>TimeUnit.SECONDS.toNanos(60)) throw new java.io.IOException("O comprovante esperou demais na fila e não foi enviado. Confira a impressora e use a segunda via.");
                destino.enviar(obter.get());resultado.complete(null);
            } catch (Throwable e) { resultado.completeExceptionally(e); }
        }
    }
    public static void encerrar() {
        encerrar(FILA);
    }
    static void encerrar(ThreadPoolExecutor fila) {
        for (Runnable r:fila.shutdownNow()) if(r instanceof ImpressaoVendaService.Trabalho) {
            ((ImpressaoVendaService.Trabalho)r).resultado.completeExceptionally(new java.io.IOException("Envio encerrado. O comprovante pode ser recuperado pela segunda via."));
        }
    }
    public static String orientacaoFalha(Throwable erro) {
        while (erro.getCause()!=null && (erro instanceof CompletionException || erro instanceof java.util.concurrent.ExecutionException)) erro=erro.getCause();
        if(erro instanceof com.casarural.sistemapdv.services.impressao.EnvioImpressaoIncertoException) return erro.getMessage();
        if(erro instanceof java.io.IOException) return erro.getMessage()+"\nConfira a impressora antes de solicitar outra via. Não repita a venda ou o pagamento.";
        return "Confira a impressora e o papel. Depois use a segunda via, sem repetir a venda ou o pagamento.";
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
        public synchronized void enviar(Comprovante comprovante) throws Exception {
            String modo = modo(System.getProperty("pdv.impressao.modo"), System.getenv("PDV_IMPRESSAO_MODO"));
            if (modo.equals("previa")) {
                previa.enviar(comprovante);
            } else {
                String nome = System.getProperty("pdv.impressora.nome", "ELGIN i7(USB)");
                new com.casarural.sistemapdv.services.impressao.DestinoProcesso(nome).enviar(comprovante);
            }
        }
    }
}
