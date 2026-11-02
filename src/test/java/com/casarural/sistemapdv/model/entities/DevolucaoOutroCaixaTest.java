package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevolucaoOutroCaixaTest {
    static BigDecimal r(String s){return new BigDecimal(s).setScale(2);}
    @Test void trocaAntigaMovimentaSomenteADiferencaEmDinheiro(){
        for(String total:List.of("80","100","120")){
            CashSummary caixa=new CashSummary(r("100"));
            caixa.addPagamento(PaymentOrigin.VENDA,PaymentMethod.DINHEIRO,r(total));
            caixa.addCompensacao(PaymentMethod.DINHEIRO,r("100"));
            assertEquals(r(total),caixa.getDinheiroEsperado());
            assertEquals(r(total),caixa.getTotalVendido()); // venda nova completa, compensação separada
        }
    }
    @Test void devolucaoAntigaAbateDinheiroPixECartaoSemVirarSangria(){
        CashSummary caixa=new CashSummary(r("100"));
        caixa.addCompensacao(PaymentMethod.DINHEIRO,r("30")).addCompensacao(PaymentMethod.PIX,r("20")).addCompensacao(PaymentMethod.CARTAO,r("10"));
        assertEquals(r("70"),caixa.getDinheiroEsperado());assertEquals(r("-20"),caixa.getTotalPix());assertEquals(r("-10"),caixa.getTotalCartao());assertEquals(r("0"),caixa.getSangrias());
    }
    @Test void recebimentoHojeContinuaContadoQuandoDevolucaoIncluiValoresDeOntem(){
        CashSummary caixa=new CashSummary(r("100"));caixa.addPagamento(PaymentOrigin.RECEBIMENTO_FIADO,PaymentMethod.DINHEIRO,r("20"));
        caixa.addCompensacao(PaymentMethod.DINHEIRO,r("70"));
        assertEquals(r("50"),caixa.getDinheiroEsperado());assertEquals(r("20"),caixa.getTotalFiadoRecebido());
    }
    @Test void vendaDeOutroCaixaExigeCaixaAtualIdentidadeEBaixaConhecida(){
        VendaParaDesfazer v=new VendaParaDesfazer();v.setStatus(OrderStatus.PAGO);v.setIdCaixaOriginal(1);
        Product p=new Product();p.setIdProduto(3);OrderItem i=new OrderItem(p,1,r("10"));i.setEstoqueBaixado(1);v.getItens().add(i);
        assertFalse(v.podeDesfazer());v.setCaixaAtualDisponivel(true);assertTrue(v.podeDesfazer());
        i.setEstoqueBaixado(null);assertFalse(v.podeDesfazer());i.setEstoqueBaixado(1);v.setIdCaixaOriginal(null);assertFalse(v.podeDesfazer());
        v.setIdCaixaOriginal(1);v.setStatus(OrderStatus.CANCELADO);assertFalse(v.podeDesfazer());
    }
    @Test void periodoSomenteComDevolucaoMostraReversaoDoLucro(){
        ResumoVendas r=new ResumoVendas();r.adicionarCustosDoPedido(r("-100"),r("-100"),r("-60"),r("-10"));
        assertTrue(r.temReceitaComCusto());assertFalse(r.temCustoParcial());assertEquals(r("-30"),r.getLucroBruto());assertEquals(new BigDecimal("100.0"),r.getCoberturaCusto());
    }
}
