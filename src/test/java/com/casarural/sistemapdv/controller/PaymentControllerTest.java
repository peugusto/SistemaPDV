package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.services.*;
import com.casarural.sistemapdv.util.Money;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tela real, serviços em memória e impressora substituída; não acessa o banco da loja. */
@ExtendWith(JavafxTestSupport.class)
class PaymentControllerTest {
    static BigDecimal r(String v) { return Money.parse(v); }
    static void set(Object o, String nome, Object valor) throws Exception {
        Field f=o.getClass().getDeclaredField(nome); f.setAccessible(true); f.set(o,valor);
    }
    static void chamar(Object o,String nome) throws Exception {
        Method m=o.getClass().getDeclaredMethod(nome); m.setAccessible(true); m.invoke(o);
    }
    static OrderItem item(String nome,String valor,boolean fiado) {
        Product p=new Product(nome,nome,r(valor),100); p.setIdProduto(1); p.setPermiteFiado(fiado);
        return new OrderItem(p,1,r(valor));
    }
    static Order venda(OrderItem... itens) {
        Order o=new Order(); o.setItemPedido(new ArrayList<>(List.of(itens))); o.aplicarDesconto(Money.ZERO); return o;
    }
    static Order mista() {
        Order o=venda(item("PERMITIDO","20",true),item("PROIBIDO","80",false)); o.aplicarDesconto(r("20")); return o;
    }
    static class Gravacoes extends OrderService {
        int chamadas; boolean falharPrimeira; Customer cliente; OrderStatus status;
        @Override public void insert(Order o) throws SQLException {
            chamadas++;
            if(falharPrimeira && chamadas==1) throw new SQLException("Falha simulada antes de gravar");
            cliente=o.getCostumer(); status=o.getStatus(); o.setIdPedido(901);
        }
    }
    static class Tela {
        final FXMLLoader loader; final PaymentController c; final Customer cliente;
        Tela(Order o,Gravacoes g) throws Exception {
            loader=new FXMLLoader(PaymentController.class.getResource("/com/casarural/sistemapdv/view/FechamentoVenda.fxml"));
            Parent root=loader.load(); Stage stage=new Stage(); stage.setScene(new Scene(root)); c=loader.getController();
            cliente=new Customer("CLIENTE FICTÍCIO",r("500"),CustomerStatus.DISPONIVEL); cliente.setIdCliente(7);
            c.setDadosVenda(o,g,new CustomerService(null) { @Override public List<Customer> findAll(){ return List.of(cliente); } });
            combo().setValue(cliente);
            set(c,"impressaoVendaService",new ImpressaoVendaService(cupom -> {},Runnable::run));
        }
        @SuppressWarnings("unchecked") ComboBox<Customer> combo(){return (ComboBox<Customer>)loader.getNamespace().get("comboCliente");}
        void metodo(String id){((ToggleButton)loader.getNamespace().get(id)).setSelected(true);}
        TextField campo(){return (TextField)loader.getNamespace().get("txtValorRecebido");}
        void pagar(String valor){campo().setText(valor); campo().getOnAction().handle(new ActionEvent());}
        String troco(){return ((Label)loader.getNamespace().get("labelTroco")).getText().replace('\u00a0',' ');}
    }
    static void responder(ButtonBar.ButtonData escolha,List<String> mensagens) {
        Platform.runLater(() -> {
            for(Window w:new ArrayList<>(Window.getWindows())) {
                if(w.getScene()!=null && w.getScene().getRoot() instanceof DialogPane) {
                    DialogPane d=(DialogPane)w.getScene().getRoot();
                    mensagens.add(d.lookupAll(".label").stream().filter(n -> n instanceof Label)
                            .map(n -> ((Label)n).getText()).reduce("",(a,b)->a+"\n"+b));
                    ButtonType b=d.getButtonTypes().stream().filter(t -> t.getButtonData()==escolha).findFirst().orElse(d.getButtonTypes().get(0));
                    ((Button)d.lookupButton(b)).fire(); return;
                }
            }
            throw new AssertionError("O aviso esperado não abriu");
        });
    }
    @Test void retirarProibidosLimpaDescontoECobraRestante() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=mista(); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g); t.metodo("btnFiado");
        List<String> mensagens=new ArrayList<>(); responder(ButtonBar.ButtonData.OTHER,mensagens); t.pagar("80");
        assertEquals(1,g.chamadas); assertEquals(1,o.getItemPedido().size());
        assertEquals(r("20"),o.getValorTotal()); assertEquals(Money.ZERO,o.getDesconto());
        assertEquals(r("20"),o.getTotalPagamentos(PaymentMethod.FIADO));
        assertTrue(mensagens.get(0).contains("desconto será removido")); assertTrue(mensagens.get(0).contains("20,00"));
    }); }
    @Test void voltarMantemCarrinhoDescontoEPagamentos() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=mista(); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g); t.metodo("btnFiado");
        responder(ButtonBar.ButtonData.CANCEL_CLOSE,new ArrayList<>()); t.pagar("80");
        assertEquals(0,g.chamadas); assertEquals(2,o.getItemPedido().size()); assertEquals(r("20"),o.getDesconto());
        assertTrue(o.getPagamentos().isEmpty());
    }); }
    @Test void pagamentosMaioresQueRestanteImpedemRetiradaAntesDeAlterar() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=mista(); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g); t.pagar("30"); t.metodo("btnFiado");
        responder(ButtonBar.ButtonData.OK_DONE,new ArrayList<>()); t.pagar("50");
        assertEquals(0,g.chamadas); assertEquals(2,o.getItemPedido().size()); assertEquals(r("20"),o.getDesconto());
        assertEquals(r("30"),o.getTotalPagamentos(PaymentMethod.DINHEIRO));
    }); }
    @Test void limiteInsuficienteNaoAlteraCarrinho() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=mista(); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g); t.cliente.setLimiteCredito(r("10")); t.metodo("btnFiado");
        responder(ButtonBar.ButtonData.OK_DONE,new ArrayList<>()); t.pagar("80");
        assertEquals(0,g.chamadas); assertEquals(2,o.getItemPedido().size()); assertEquals(r("20"),o.getDesconto());
    }); }
    @Test void retiradaRecalculaCreditosDaTrocaSemCriarPagamentoZero() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=mista(); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g);
        VendaParaDesfazer troca=new VendaParaDesfazer(); troca.setIdPedido(100); troca.getPagoPorForma().put(PaymentMethod.DINHEIRO,r("30"));
        t.c.setTroca(troca); t.metodo("btnFiado");
        responder(ButtonBar.ButtonData.OTHER,new ArrayList<>());
        // A confirmação da troca é outro diálogo modal, agendado depois da escolha dos itens.
        Platform.runLater(() -> responder(ButtonBar.ButtonData.OK_DONE,new ArrayList<>()));
        t.pagar("50");
        assertEquals(1,g.chamadas); assertEquals(r("20"),o.getValorTotal()); assertEquals(1,o.getPagamentos().size());
        assertTrue(o.getPagamentos().get(0).isCredito()); assertEquals(r("20"),o.getPagamentos().get(0).getValor());
    }); }
    @Test void falhaNoFiadoSeguidaDePagamentoAVistaNaoMantemCliente() throws Exception { JavafxTestSupport.executar(() -> {
        for(String forma:List.of("btnDinheiro","btnPix","btnCartao")) {
            Order o=venda(item("PRODUTO","10",true)); Gravacoes g=new Gravacoes(); g.falharPrimeira=true;
            Tela t=new Tela(o,g); t.metodo("btnFiado"); t.pagar("10"); JavafxTestSupport.fecharJanelas();
            assertNull(o.getCostumer()); t.metodo(forma); t.pagar("10");
            assertEquals(2,g.chamadas); assertEquals(OrderStatus.PAGO,g.status); assertNull(g.cliente);
            JavafxTestSupport.fecharJanelas();
        }
    }); }
    @Test void repetirFiadoAposFalhaPreservaClienteCorreto() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=venda(item("PRODUTO","10",true)); Gravacoes g=new Gravacoes(); g.falharPrimeira=true;
        Tela t=new Tela(o,g); t.metodo("btnFiado"); t.pagar("10"); JavafxTestSupport.fecharJanelas(); t.pagar("10");
        assertEquals(2,g.chamadas); assertEquals(OrderStatus.FIADO,g.status); assertSame(t.cliente,g.cliente);
    }); }
    @Test void vendaPagaDeTrocaPreservaClienteDaVendaOriginal() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=venda(item("PRODUTO","20",true)); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g);
        VendaParaDesfazer troca=new VendaParaDesfazer(); troca.setIdPedido(100); troca.setIdCliente(7);
        troca.getPagoPorForma().put(PaymentMethod.DINHEIRO,r("10")); t.c.setTroca(troca);
        responder(ButtonBar.ButtonData.OK_DONE,new ArrayList<>()); t.pagar("10");
        assertEquals(OrderStatus.PAGO,g.status); assertSame(t.cliente,g.cliente); assertEquals(1,g.chamadas);
    }); }
    @Test void trocoAcompanhaFormaAtualSemRedigitarValor() throws Exception { JavafxTestSupport.executar(() -> {
        Tela t=new Tela(venda(item("PRODUTO","10",true)),new Gravacoes()); t.campo().setText("20");
        assertEquals("R$ 10,00",t.troco());
        for(String forma:List.of("btnPix","btnCartao","btnFiado")) {t.metodo(forma); assertEquals("R$ 0,00",t.troco());}
        t.metodo("btnDinheiro"); assertEquals("R$ 10,00",t.troco());
        t.campo().clear(); assertEquals("R$ 0,00",t.troco());
    }); }
    @Test void trocoAcompanhaDescontoECreditosComMesmoValorDigitado() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=venda(item("PRODUTO","10",true)); Tela t=new Tela(o,new Gravacoes()); t.campo().setText("20");
        o.aplicarDesconto(r("2")); chamar(t.c,"aplicarCreditos"); chamar(t.c,"atualizarExibicao"); assertEquals("R$ 12,00",t.troco());
        VendaParaDesfazer troca=new VendaParaDesfazer(); troca.getPagoPorForma().put(PaymentMethod.PIX,r("3"));
        t.c.setTroca(troca); assertEquals("R$ 15,00",t.troco());
    }); }
    @Test void pagamentoEmDinheiroRegistraApenasSaldoEGuardaTroco() throws Exception { JavafxTestSupport.executar(() -> {
        Order o=venda(item("PRODUTO","10",true)); Gravacoes g=new Gravacoes(); Tela t=new Tela(o,g); t.pagar("20");
        assertEquals(r("10"),o.getTotalPagamentos(PaymentMethod.DINHEIRO));
        Field f=PaymentController.class.getDeclaredField("trocoDinheiro"); f.setAccessible(true); assertEquals(r("10"),f.get(t.c));
        assertTrue(t.c.isVendaConcluida());
    }); }
}
