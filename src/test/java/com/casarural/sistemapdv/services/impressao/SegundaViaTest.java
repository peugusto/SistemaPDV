package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.dao.RelatorioImpressaoDao;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado.Tipo;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.support.ArquivoComprovanteMemoria;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SegundaViaTest {
    static Comprovante texto(String valor) {return new Comprovante(){public String getTexto(){return valor;}public String getNomeTrabalho(){return "Teste";}};}
    static class Relatorios implements RelatorioImpressaoDao {
        boolean recusado; int venda,cliente; Integer recebimento; LocalDateTime data; PaymentMethod forma;
        public ExtratoFiado extratoFiado(int id){throw new AssertionError();}
        public FechamentoCaixa fechamentoCaixa(int id){throw new AssertionError();}
        public Comprovante vendaHistorica(int id){if(recusado)throw new IllegalArgumentException("Venda cancelada");venda=id;return texto("HISTORICO DA VENDA");}
        public Comprovante recebimentoHistorico(int c,Integer r,LocalDateTime d,PaymentMethod f){if(recusado)throw new IllegalArgumentException("Recebimento desfeito");cliente=c;recebimento=r;data=d;forma=f;return texto("HISTORICO DO FIADO");}
    }
    @Test void erroDeImpressoraPreservaOriginalEPermiteSegundaViaEmOutroServico() {
        ArquivoComprovanteMemoria arq=new ArquivoComprovanteMemoria();Relatorios rel=new Relatorios();
        ImpressaoVendaService original=new ImpressaoVendaService(c->{throw new java.io.IOException("Sem papel");},Runnable::run,rel,arq);
        Order venda=ComprovanteVendaTest.venda();
        venda.getPagamentos().add(new Order.PaymentSlice(new BigDecimal("40"),PaymentMethod.DINHEIRO));
        assertThrows(CompletionException.class,()->original.emitir(venda,new BigDecimal("10"),Map.of()).join());
        String guardado=arq.buscar(Tipo.VENDA,String.valueOf(venda.getIdPedido())).orElseThrow().getTexto();
        venda.getItemPedido().clear();List<Comprovante> enviados=new ArrayList<>();
        new ImpressaoVendaService(enviados::add,Runnable::run,rel,arq).reimprimirVenda(venda.getIdPedido()).join();
        assertEquals(1,enviados.size());assertTrue(enviados.get(0).getTexto().endsWith(guardado));
        assertTrue(enviados.get(0).getTexto().contains("SEGUNDA VIA"));assertTrue(enviados.get(0).getTexto().contains("Troco"));
        assertEquals(1,arq.documentos.size());
    }
    @Test void segundaViaNaoSubstituiOriginalNemAcrescentaOutroRegistro() {
        ArquivoComprovanteMemoria arq=new ArquivoComprovanteMemoria();Relatorios rel=new Relatorios();List<Comprovante> enviados=new ArrayList<>();
        ImpressaoVendaService s=new ImpressaoVendaService(enviados::add,Runnable::run,rel,arq);Order v=ComprovanteVendaTest.venda();
        s.emitir(v,BigDecimal.ZERO,Map.of()).join();String original=enviados.get(0).getTexto();v.getItemPedido().get(0).getProduto().setNomeProduto("OUTRO NOME");
        s.emitir(v,BigDecimal.ZERO,Map.of()).join();assertEquals(original,enviados.get(1).getTexto());s.reimprimirVenda(v.getIdPedido()).join();
        assertEquals(1,arq.documentos.size());assertTrue(enviados.get(2).getTexto().endsWith(original));
    }
    @Test void ultimaListaMantemQuantidadesMesmoDepoisDeEditarEReabrir() {
        ArquivoComprovanteMemoria arq=new ArquivoComprovanteMemoria();List<Comprovante> enviados=new ArrayList<>();List<Runnable> fila=new ArrayList<>();
        Product p=new Product("1","Arroz",BigDecimal.TEN,2);RegraListaCompras.Item i=RegraListaCompras.acumulado(p,7);
        ImpressaoVendaService s=new ImpressaoVendaService(enviados::add,fila::add,new Relatorios(),arq);
        s.emitirListaCompras(List.of(i),LocalDateTime.of(2026,11,1,9,0));i.setComprar(99);p.setNomeProduto("ALTERADO");fila.remove(0).run();
        String original=enviados.get(0).getTexto();new ImpressaoVendaService(enviados::add,Runnable::run,new Relatorios(),arq).reimprimirUltimaLista().join();
        assertTrue(enviados.get(1).getTexto().endsWith(original));assertTrue(original.contains("Arroz"));assertFalse(original.contains("ALTERADO"));
        assertEquals(99,i.getComprar());assertEquals(1,arq.documentos.size());
    }
    @Test void capturaIdentidadeDoRecebimentoAntesDaFilaENaoUsaObjetoEditado() {
        Relatorios rel=new Relatorios();ArquivoComprovanteMemoria arq=new ArquivoComprovanteMemoria();List<Runnable> fila=new ArrayList<>();
        Payment p=new Payment();p.setIdRecebimento(77);LocalDateTime data=LocalDateTime.of(2026,11,1,10,0);p.setDataPagamento(data);p.setMetodo(PaymentMethod.PIX);
        arq.guardar(new ComprovanteGuardado(Tipo.FIADO,"77",texto("RECIBO ORIGINAL")));List<Comprovante> enviados=new ArrayList<>();
        CompletableFuture<Void> f=new ImpressaoVendaService(enviados::add,fila::add,rel,arq).reimprimirRecebimento(9,p);
        p.setIdRecebimento(99);p.setMetodo(PaymentMethod.DINHEIRO);fila.remove(0).run();f.join();
        assertEquals(77,rel.recebimento);assertEquals(data,rel.data);assertEquals(PaymentMethod.PIX,rel.forma);assertEquals(9,rel.cliente);
        assertTrue(enviados.get(0).getTexto().endsWith("RECIBO ORIGINAL"));
    }
    @Test void originalArquivadoNaoLiberaComprovanteDeOperacaoDesfeita() {
        Relatorios rel=new Relatorios();rel.recusado=true;ArquivoComprovanteMemoria arq=new ArquivoComprovanteMemoria();arq.guardar(new ComprovanteGuardado(Tipo.VENDA,"1",texto("ORIGINAL")));
        List<Comprovante> enviados=new ArrayList<>();ImpressaoVendaService s=new ImpressaoVendaService(enviados::add,Runnable::run,rel,arq);
        assertThrows(CompletionException.class,()->s.reimprimirVenda(1).join());assertTrue(enviados.isEmpty());
    }
    @Test void semOriginalUsaHistoricoESemListaNaoEnviaNada() {
        List<Comprovante> enviados=new ArrayList<>();ImpressaoVendaService s=new ImpressaoVendaService(enviados::add,Runnable::run,new Relatorios(),new ArquivoComprovanteMemoria());
        s.reimprimirVenda(1).join();assertTrue(enviados.get(0).getTexto().endsWith("HISTORICO DA VENDA"));
        assertThrows(CompletionException.class,()->s.reimprimirUltimaLista().join());assertEquals(1,enviados.size());
    }
    @Test void larguraDaSegundaViaCabeNoFormatoTermico() {
        Comprovante c=SegundaVia.de(ComprovanteVenda.gerar(ComprovanteVendaTest.venda(),BigDecimal.ZERO,Map.of()),LocalDateTime.of(2026,11,1,10,0));
        assertTrue(c.getTexto().lines().allMatch(l->l.length()<=36));
    }
    @Test void falhaAoGuardarNaoEnviaUmComprovanteSemRecuperacao() {
        ArquivoComprovanteMemoria arq=new ArquivoComprovanteMemoria(){public ComprovanteGuardado guardar(ComprovanteGuardado d){throw new IllegalStateException("Sem banco");}};
        List<Comprovante> enviados=new ArrayList<>();ImpressaoVendaService s=new ImpressaoVendaService(enviados::add,Runnable::run,new Relatorios(),arq);
        assertThrows(CompletionException.class,()->s.emitir(ComprovanteVendaTest.venda(),BigDecimal.ZERO,Map.of()).join());assertTrue(enviados.isEmpty());
    }
}
