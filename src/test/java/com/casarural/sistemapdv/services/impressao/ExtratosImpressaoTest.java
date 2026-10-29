package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.dao.RelatorioImpressaoDao;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.services.ImpressaoVendaService;
import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class ExtratosImpressaoTest {
    static BigDecimal r(String v) { return Money.parse(v); }
    static final LocalDateTime DATA = LocalDateTime.of(2026,10,29,18,30);
    static Customer cliente() { Customer c = new Customer("Maria da Silva",r("500"),CustomerStatus.DISPONIVEL); c.setIdCliente(7); c.setTelefone("11987654321"); return c; }
    static Order compra(int id, String produto, String bruto, String desconto, String pagoVenda, String pagoDepois) {
        Order p = new Order(); p.setIdPedido(id); p.setDataPedido(DATA.minusDays(id)); p.setStatus(OrderStatus.FIADO);
        Product item = new Product("TESTE",produto,r(bruto),10);
        p.setItemPedido(new ArrayList<>(List.of(new OrderItem(item,1,r(bruto))))); p.aplicarDesconto(r(desconto));
        p.setPagoNaVenda(r(pagoVenda)); p.setRecebidoDepois(r(pagoDepois)); return p;
    }
    static ExtratoFiado extrato() { return new ExtratoFiado(cliente(),DATA,List.of(compra(2,"Arroz","50","5","10","15"),compra(1,"Café","20","0","0","0"))); }
    static FechamentoCaixa fechamento() {
        CashRegister c = new CashRegister(); c.setIdCaixa(3); c.setAbertoEm(DATA.minusHours(10)); c.setFechadoEm(DATA);
        c.setValorInicial(r("100")); c.setValorEsperado(r("105")); c.setValorContado(r("104"));
        Order a = compra(1,"Arroz","20","0","20","0"); a.setStatus(OrderStatus.PAGO); a.getPagamentos().add(new Order.PaymentSlice(r("20"),PaymentMethod.DINHEIRO));
        Order b = compra(2,"Café","30","0","0","0"); b.getPagamentos().add(new Order.PaymentSlice(r("30"),PaymentMethod.FIADO));
        Order cancelada = compra(3,"Venda cancelada","999","0","0","0"); cancelada.setStatus(OrderStatus.CANCELADO);
        CashMovement m = new CashMovement(); m.setIdMovimento(9); m.setDataMovimento(DATA.minusHours(1)); m.setTipo(CashMovementType.SANGRIA); m.setValor(r("25")); m.setMotivo("Pagamento do fornecedor");
        Payment recebido = new Payment(a,r("10"),DATA.minusMinutes(20),PaymentMethod.DINHEIRO); recebido.setOrigem(PaymentOrigin.RECEBIMENTO_FIADO);
        CashSummary s = new CashSummary(r("100")).addPagamento(PaymentOrigin.VENDA,PaymentMethod.DINHEIRO,r("20"))
                .addPagamento(PaymentOrigin.VENDA,PaymentMethod.FIADO,r("30"))
                .addPagamento(PaymentOrigin.RECEBIMENTO_FIADO,PaymentMethod.DINHEIRO,r("10"))
                .addMovimento(CashMovementType.SANGRIA,r("25"));
        return new FechamentoCaixa(c,s,List.of(a,b,cancelada),List.of(m),List.of(recebido));
    }
    static String linha(String texto,String prefixo) { return texto.lines().filter(l -> l.startsWith(prefixo)).findFirst().orElseThrow(); }
    @Test void extratoListaProdutosPorDiaEAbatePagamentosParciaisEDesconto() {
        String t = ComprovanteDivida.gerar(extrato()).getTexto();
        assertTrue(t.contains("Maria da Silva")); assertTrue(t.contains("Arroz")); assertTrue(t.contains("Café"));
        assertTrue(linha(t,"TOTAL DAS COMPRAS").contains("65,00"));
        assertTrue(linha(t,"JÁ PAGO NESSAS COMPRAS").contains("25,00"));
        assertTrue(linha(t,"TOTAL DEVENDO").contains("40,00"));
        assertTrue(linha(t,"Descontos").contains("5,00")); assertTrue(t.endsWith("Obrigado pela preferência!\n"));
        assertFalse(t.contains("PAGO AGORA"));
        assertTrue(t.indexOf("27/10/2026") < t.indexOf("28/10/2026"));
    }
    @Test void extratoSemDividaNaoInventaComprasOuRecebimento() {
        String t = ComprovanteDivida.gerar(new ExtratoFiado(cliente(),DATA,List.of())).getTexto();
        assertTrue(t.contains("Nenhuma dívida")); assertTrue(linha(t,"TOTAL DEVENDO").endsWith("0,00"));
    }
    @Test void mesmoDiaApareceUmaVezEProdutosContinuamInteiros() {
        ExtratoFiado e = extrato(); e.getCompras().get(1).setDataPedido(e.getCompras().get(0).getDataPedido());
        String t = ComprovanteDivida.gerar(e).getTexto(); assertEquals(1,t.lines().filter(l -> l.contains("27/10/2026")).count());
        assertTrue(t.contains("Arroz")); assertTrue(t.contains("Café"));
    }
    @Test void fechamentoIncluiVendasRetiradasFiadoEConferencia() {
        String t = ComprovanteFechamento.gerar(fechamento()).getTexto();
        assertTrue(t.contains("Venda #1")); assertTrue(t.contains("Venda #2")); assertTrue(t.contains("Arroz"));
        assertTrue(t.contains("Retirada #9")); assertTrue(t.contains("Pagamento do fornecedor"));
        assertTrue(t.contains("CANCELADA / TROCADA")); assertTrue(t.contains("RECEBIMENTOS DE FIADO"));
        assertTrue(linha(t,"TOTAL VENDIDO").endsWith("50,00")); assertTrue(linha(t,"TOTAL RETIRADO").endsWith("25,00"));
        assertTrue(linha(t,"ESPERADO NA GAVETA").endsWith("105,00")); assertTrue(linha(t,"CONTADO").endsWith("104,00"));
        assertTrue(t.contains("-1,00")); assertFalse(t.contains("ATENÇÃO:"));
    }
    @Test void caixaSemConferenciaNaoMostraDiferencaZero() {
        FechamentoCaixa f = fechamento(); f.getCaixa().setValorContado(null);
        String t = ComprovanteFechamento.gerar(f).getTexto(); assertTrue(t.contains("Gaveta não conferida")); assertFalse(t.contains("DIFERENÇA"));
    }
    @Test void divergenciaEntreLancamentosEFechamentoEExplicita() {
        FechamentoCaixa f = fechamento(); f.getCaixa().setValorEsperado(r("100"));
        String t = ComprovanteFechamento.gerar(f).getTexto(); assertTrue(t.contains("ATENÇÃO:"));
        assertTrue(linha(t,"ESPERADO NA GAVETA").endsWith("100,00")); assertTrue(linha(t,"Saldo pelos lançamentos").endsWith("105,00"));
    }
    @Test void caixaAbertoNaoGeraFechamento() {
        FechamentoCaixa f = fechamento(); f.getCaixa().setFechadoEm(null);
        assertThrows(IllegalArgumentException.class,()->ComprovanteFechamento.gerar(f));
    }
    @Test void nomesLongosAcentosECaracteresDeControleCabemNasEtiquetas() {
        ExtratoFiado e = extrato(); e.getCliente().setNomeCliente("Nome muito longo com acentos e outros detalhes para o cliente");
        e.getCompras().get(0).getItemPedido().get(0).getProduto().setNomeProduto("PRODUTO MUITO LONGO ".repeat(6)+"\u001b comando\n Café");
        for(Comprovante c:List.of(ComprovanteDivida.gerar(e),ComprovanteFechamento.gerar(fechamento()))) {
            String[] linhas = c.getTexto().split("\n");
            for(String l:linhas) assertTrue(l.length()<=36,"Linha excedeu largura: "+l);
            assertFalse(c.getTexto().contains("\u001b"));
            List<String> paginadas = ImpressoraTermica.paginar(linhas,7).stream().flatMap(List::stream).collect(Collectors.toList());
            assertEquals(List.of(linhas),paginadas);
        }
    }
    @Test void consultasNovasOcorremNaFilaESoUmaVezPorSolicitacao() {
        List<Runnable> fila = new ArrayList<>(); List<Comprovante> enviados = new ArrayList<>(); int[] leituras={0,0};
        RelatorioImpressaoDao dao = new RelatorioImpressaoDao() {
            public ExtratoFiado extratoFiado(int id) { assertEquals(7,id); leituras[0]++; return extrato(); }
            public FechamentoCaixa fechamentoCaixa(int id) { assertEquals(3,id); leituras[1]++; return fechamento(); }
        };
        ImpressaoVendaService s = new ImpressaoVendaService(enviados::add,fila::add,dao);
        CompletableFuture<Void> a=s.emitirDivida(7), b=s.emitirFechamento(3);
        assertEquals(0,leituras[0]+leituras[1]); fila.forEach(Runnable::run); a.join(); b.join();
        assertArrayEquals(new int[]{1,1},leituras); assertEquals(2,enviados.size());
    }
    @Test void falhaDeConsultaOuImpressoraNaoRepeteAutomaticamente() {
        int[] leituras={0};
        RelatorioImpressaoDao dao = new RelatorioImpressaoDao() {
            public ExtratoFiado extratoFiado(int id) { leituras[0]++; throw new IllegalStateException("Banco indisponível"); }
            public FechamentoCaixa fechamentoCaixa(int id) { return fechamento(); }
        };
        ImpressaoVendaService s = new ImpressaoVendaService(c -> { throw new IOException("Sem papel"); },Runnable::run,dao);
        assertThrows(CompletionException.class,()->s.emitirDivida(7).join()); assertEquals(1,leituras[0]);
        assertThrows(CompletionException.class,()->s.emitirFechamento(3).join()); assertEquals(1,leituras[0]);
    }
}
