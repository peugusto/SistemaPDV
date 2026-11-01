package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.dao.RelatorioImpressaoDao;
import com.casarural.sistemapdv.model.dao.ArquivoComprovanteDao;
import com.casarural.sistemapdv.model.dao.impl.ArquivoComprovanteDaoJdbc;
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
    public interface Destino { void enviar(Comprovante comprovante) throws Exception; }

    // Uma fila preserva a ordem das vendas. A thread termina após ficar ociosa e não abandona
    // um envio em andamento quando a janela do sistema é fechada.
    private static final Executor FILA = new ThreadPoolExecutor(0, 1, 2, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), tarefa -> new Thread(tarefa, "impressao-comprovantes"));
    private final Destino destino;
    private final Executor executor;
    private final RelatorioImpressaoDao relatorios;
    private final ArquivoComprovanteDao arquivo;

    public ImpressaoVendaService() {
        this(new DestinoConfigurado(), FILA);
    }

    public ImpressaoVendaService(Destino destino, Executor executor) {
        this(destino, executor, DaoFactory.createRelatorioImpressaoDAO());
    }
    public ImpressaoVendaService(Destino destino, Executor executor, RelatorioImpressaoDao relatorios) {
        this(destino, executor, relatorios, new ArquivoComprovanteDaoJdbc());
    }
    public ImpressaoVendaService(Destino destino, Executor executor, RelatorioImpressaoDao relatorios, ArquivoComprovanteDao arquivo) {
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
        return enviar(() -> arquivo.guardar(captura));
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
        return CompletableFuture.runAsync(() -> {
            try {
                destino.enviar(obter.get());
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
        public synchronized void enviar(Comprovante comprovante) throws Exception {
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
