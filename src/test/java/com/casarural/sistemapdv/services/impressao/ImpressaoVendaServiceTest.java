package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class ImpressaoVendaServiceTest {
    @Test
    void limparCarrinhoAntesDoEnvioNaoMudaOComprovante() {
        List<Runnable> fila = new ArrayList<>();
        List<Comprovante> enviados = new ArrayList<>();
        ImpressaoVendaService service = new ImpressaoVendaService(enviados::add, fila::add);
        Order venda = ComprovanteVendaTest.venda();
        CompletableFuture<Void> envio = service.emitir(venda, Money.ZERO, Map.of());
        venda.getItemPedido().clear();
        venda.setValorTotal(Money.ZERO);
        assertTrue(enviados.isEmpty());
        fila.get(0).run();
        envio.join();
        assertEquals(1, enviados.size());
        assertTrue(enviados.get(0).getTexto().contains("Arroz 5 kg"));
        assertTrue(enviados.get(0).getTexto().contains("R$ 40,00"));
    }

    @Test
    void falhaDeEnvioEhReportadaSemRepeticaoAutomatica() {
        int[] chamadas = {0};
        ImpressaoVendaService service = new ImpressaoVendaService(c -> {
            chamadas[0]++;
            throw new IOException("Impressora indisponível");
        }, Runnable::run);
        CompletableFuture<Void> envio = service.emitir(ComprovanteVendaTest.venda(), Money.ZERO, Map.of());
        CompletionException erro = assertThrows(CompletionException.class, envio::join);
        assertInstanceOf(IOException.class, erro.getCause());
        assertEquals(1, chamadas[0]);
    }

    @Test
    void producaoNaoAtivaPreviaSemOpcaoExplicita() {
        assertEquals("impressora", ImpressaoVendaService.modo(null, null));
        assertEquals("previa", ImpressaoVendaService.modo(null, "previa"));
        assertEquals("previa", ImpressaoVendaService.modo("previa", null));
        assertEquals("impressora", ImpressaoVendaService.modo("impressora", "previa"));
        assertThrows(IllegalArgumentException.class, () -> ImpressaoVendaService.modo("prevai", null));
    }
}
