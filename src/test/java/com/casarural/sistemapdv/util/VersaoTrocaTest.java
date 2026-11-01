package com.casarural.sistemapdv.util;

import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class VersaoTrocaTest {
    static final LocalDateTime DATA = LocalDateTime.of(2026,11,1,12,0);
    static VendaParaDesfazer venda() {
        VendaParaDesfazer v = new VendaParaDesfazer();
        v.setIdPedido(1);v.setStatus(OrderStatus.FIADO);v.setDataPedido(DATA);v.setValorTotal(new BigDecimal("100"));
        return v;
    }
    static String versao(VendaParaDesfazer v,int pagamento,Integer recebimento,String valor) {
        VersaoTroca resumo = new VersaoTroca(v);
        resumo.pagamento(pagamento,"PIX",new BigDecimal(valor),"RECEBIMENTO_FIADO",DATA,1,recebimento);
        return resumo.concluir();
    }
    @Test void identidadeDiferenteComMesmoValorFormaEDataInvalidaReferencia() {
        String antes=versao(venda(),10,10,"50");
        assertNotEquals(antes,versao(venda(),11,11,"50"));
        assertNotEquals(antes,versao(venda(),10,11,"50"));
        assertNotEquals(antes,versao(venda(),10,null,"50"));
        assertEquals(antes,versao(venda(),10,10,"50.00"));
    }
    @Test void mudancasNaVendaOriginalInvalidamReferenciaMesmoSemNovoPagamento() {
        String antes=versao(venda(),10,10,"50");VendaParaDesfazer v=venda();v.setStatus(OrderStatus.PAGO);
        assertNotEquals(antes,versao(v,10,10,"50"));v=venda();v.setIdCliente(9);
        assertNotEquals(antes,versao(v,10,10,"50"));v=venda();v.setDesconto(BigDecimal.ONE);
        assertNotEquals(antes,versao(v,10,10,"50"));
    }
}
