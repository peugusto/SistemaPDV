package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado.Tipo;
import com.casarural.sistemapdv.support.ArquivoComprovanteMemoria;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class FilaImpressaoTest {
    static Order venda(int id){Product p=new Product("FICTICIO","PRODUTO FICTICIO",BigDecimal.TEN,1);p.setIdProduto(1);Order o=new Order();o.setIdPedido(id);o.setItemPedido(new ArrayList<>(List.of(new OrderItem(p,1,BigDecimal.TEN))));o.aplicarDesconto(BigDecimal.ZERO);return o;}
    @Test void filaCheiaMantemOriginalGuardadoENaoRepeteEnvio()throws Exception {
        ThreadPoolExecutor fila=ImpressaoVendaService.criarFila(1);CountDownLatch entrou=new CountDownLatch(1),liberar=new CountDownLatch(1);AtomicInteger enviados=new AtomicInteger();
        ArquivoComprovanteMemoria arquivo=new ArquivoComprovanteMemoria();
        ImpressaoVendaService s=new ImpressaoVendaService(c->{entrou.countDown();liberar.await();enviados.incrementAndGet();},fila,null,arquivo);
        try {
            CompletableFuture<Void> a=s.emitir(venda(1),BigDecimal.ZERO,Map.of());assertTrue(entrou.await(3,TimeUnit.SECONDS));
            CompletableFuture<Void> b=s.emitir(venda(2),BigDecimal.ZERO,Map.of()),c=s.emitir(venda(3),BigDecimal.ZERO,Map.of());
            assertThrows(CompletionException.class,c::join);assertTrue(arquivo.buscar(Tipo.VENDA,"3").isPresent());
            liberar.countDown();a.get(3,TimeUnit.SECONDS);b.get(3,TimeUnit.SECONDS);assertEquals(2,enviados.get());
        }finally{liberar.countDown();ImpressaoVendaService.encerrar(fila);}
    }
    @Test void trabalhoAntigoNaoSaiAutomaticamenteDepoisDeLongaEspera() {
        List<Runnable> fila=new ArrayList<>();AtomicLong agora=new AtomicLong();AtomicInteger enviados=new AtomicInteger();ArquivoComprovanteMemoria arquivo=new ArquivoComprovanteMemoria();
        ImpressaoVendaService s=new ImpressaoVendaService(c->enviados.incrementAndGet(),fila::add,null,arquivo,agora::get);
        CompletableFuture<Void> f=s.emitir(venda(1),BigDecimal.ZERO,Map.of());agora.set(TimeUnit.SECONDS.toNanos(61));fila.get(0).run();
        assertThrows(CompletionException.class,f::join);assertEquals(0,enviados.get());assertTrue(arquivo.buscar(Tipo.VENDA,"1").isPresent());
    }
    @Test void encerramentoCancelaFilaSemPerderDocumento()throws Exception {
        ThreadPoolExecutor fila=ImpressaoVendaService.criarFila(1);CountDownLatch entrou=new CountDownLatch(1);ArquivoComprovanteMemoria arquivo=new ArquivoComprovanteMemoria();
        ImpressaoVendaService s=new ImpressaoVendaService(c->{entrou.countDown();new CountDownLatch(1).await();},fila,null,arquivo);
        CompletableFuture<Void> ativo=s.emitir(venda(1),BigDecimal.ZERO,Map.of());assertTrue(entrou.await(3,TimeUnit.SECONDS));CompletableFuture<Void> pendente=s.emitir(venda(2),BigDecimal.ZERO,Map.of());
        ImpressaoVendaService.encerrar(fila);assertThrows(ExecutionException.class,()->ativo.get(3,TimeUnit.SECONDS));assertThrows(CompletionException.class,pendente::join);
        assertTrue(arquivo.buscar(Tipo.VENDA,"2").isPresent());
    }
}
