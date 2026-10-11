package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CashSummaryTest {

    private static BigDecimal r(String valor) {
        return Money.parse(valor);
    }

    @Test
    void caixaRecemAbertoTemSoOTroco() {
        CashSummary s = new CashSummary(r("100"));
        assertEquals(r("100"), s.getDinheiroEsperado());
        assertEquals(Money.ZERO, s.getTotalPix());
        assertEquals(Money.ZERO, s.getTotalVendido());
    }

    @Test
    void diaCompletoDoExemploDoDesenho() {
        // troco 100 + vendas dinheiro 230 + fiado recebido em dinheiro 20 - sangria 50 = 300
        CashSummary s = new CashSummary(r("100"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.DINHEIRO, r("230"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.PIX, r("125"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.CARTAO, r("80"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.FIADO, r("35"))
                .addPagamento(PaymentOrigin.RECEBIMENTO_FIADO, PaymentMethod.DINHEIRO, r("20"))
                .addPagamento(PaymentOrigin.RECEBIMENTO_FIADO, PaymentMethod.PIX, r("20"))
                .addMovimento(CashMovementType.SANGRIA, r("50"));

        assertEquals(r("300"), s.getDinheiroEsperado());
        assertEquals(r("145"), s.getTotalPix());          // 125 de vendas + 20 de fiado recebido
        assertEquals(r("80"), s.getTotalCartao());
        assertEquals(r("35"), s.getVendidoNoFiado());
        assertEquals(r("470"), s.getTotalVendido());      // 230 + 125 + 80 + 35
        assertEquals(r("40"), s.getTotalFiadoRecebido());
    }

    @Test
    void vendaNoFiadoNaoEntraNaGaveta() {
        CashSummary s = new CashSummary(r("50"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.FIADO, r("200"));
        assertEquals(r("50"), s.getDinheiroEsperado());
        assertEquals(r("200"), s.getTotalVendido());
    }

    @Test
    void pixECartaoNaoEntramNaGaveta() {
        CashSummary s = new CashSummary(r("50"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.PIX, r("40"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.CARTAO, r("60"));
        assertEquals(r("50"), s.getDinheiroEsperado());
    }

    @Test
    void sangriaTiraEReforcoColocaNaGaveta() {
        CashSummary s = new CashSummary(r("100"))
                .addMovimento(CashMovementType.REFORCO, r("30"))
                .addMovimento(CashMovementType.SANGRIA, r("45,50"))
                .addMovimento(CashMovementType.SANGRIA, r("4,50"));
        assertEquals(r("80"), s.getDinheiroEsperado());   // 100 + 30 - 45,50 - 4,50
        assertEquals(r("50"), s.getSangrias());
        assertEquals(r("30"), s.getReforcos());
    }

    @Test
    void variosLancamentosDaMesmaFormaSaoSomados() {
        CashSummary s = new CashSummary(Money.ZERO)
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.DINHEIRO, r("0,10"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.DINHEIRO, r("0,20"));
        assertEquals(r("0,30"), s.getDinheiroEsperado());
    }

    @Test
    void diferencaNaContagem() {
        CashSummary s = new CashSummary(r("100"))
                .addPagamento(PaymentOrigin.VENDA, PaymentMethod.DINHEIRO, r("50"));
        assertEquals(r("0"), s.diferenca(r("150")));      // bateu
        assertEquals(r("-5"), s.diferenca(r("145")));     // faltou 5
        assertEquals(r("2"), s.diferenca(r("152")));      // sobrou 2
    }

    @Test
    void caixaSemConferenciaNaoTemDiferenca() {
        CashRegister caixa = new CashRegister();
        caixa.setValorEsperado(r("150"));
        assertNull(caixa.getDiferenca());                 // não contou: não inventa diferença zero
        caixa.setValorContado(r("148"));
        assertEquals(r("-2"), caixa.getDiferenca());
    }
}
