package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.services.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class SegundaViaTelasTest {
    static void set(Object o,String n,Object v)throws Exception {Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static class Imp extends ImpressaoVendaService {
        int chamadas,venda,cliente;Payment recebimento;final CompletableFuture<Void> envio=new CompletableFuture<>();
        Imp(){super(d->{},Runnable::run);}
        public CompletableFuture<Void> reimprimirVenda(int id){chamadas++;venda=id;return envio;}
        public CompletableFuture<Void> reimprimirRecebimento(int c,Payment p){chamadas++;cliente=c;recebimento=p;return envio;}
        public CompletableFuture<Void> reimprimirUltimaLista(){chamadas++;return envio;}
    }
    static SaleSummary venda(int id,OrderStatus status) {
        SaleSummary v=new SaleSummary();v.setIdPedido(id);v.setDataPedido(LocalDateTime.of(2026,11,1,10,0));v.setStatus(status);v.setValorTotal(BigDecimal.TEN);return v;
    }
    static class Vendas extends OrderService {
        public List<SaleSummary> findSales(SaleFilter f,int limite){return List.of(venda(12,OrderStatus.PAGO),venda(13,OrderStatus.CANCELADO));}
        public List<OrderItem> findItemsByOrderId(int id){return List.of();}
        public List<Payment> findPaymentsByOrder(int id){return List.of();}
        public Optional<VendaParaDesfazer> findParaDesfazer(int id){return Optional.empty();}
        public void insert(Order o){throw new AssertionError("Segunda via tentou vender");}
        public RecebimentoFiado registerPartialPayment(Integer id,BigDecimal v,PaymentMethod f){throw new AssertionError("Segunda via tentou receber");}
    }
    static FXMLLoader tela(String nome)throws Exception {
        FXMLLoader l=new FXMLLoader(SegundaViaTelasTest.class.getResource("/com/casarural/sistemapdv/view/"+nome+".fxml"));
        if(nome.equals("order-list")) {OrderListController c=new OrderListController();set(c,"service",new Vendas());l.setControllerFactory(t->c);}
        Parent p=l.load();Stage s=new Stage();s.setScene(new Scene(p));com.casarural.sistemapdv.util.Tema.aplicar(s.getScene());return l;
    }
    @SuppressWarnings("unchecked") static <T> TableView<T> tabela(FXMLLoader l,String n){return (TableView<T>)l.getNamespace().get(n);}
    static Button botao(FXMLLoader l,String n){return (Button)l.getNamespace().get(n);}
    @Test void historicoImprimeSomenteSelecionadaUmaVezENaoPermiteCancelada()throws Exception {
        JavafxTestSupport.executar(()->{
            FXMLLoader l=tela("order-list");OrderListController c=l.getController();Imp i=new Imp();set(c,"impressaoVendaService",i);
            c.onSegundaVia();assertEquals(0,i.chamadas);tabela(l,"tabelaVendas").getSelectionModel().select(0);
            assertFalse(botao(l,"btnSegundaVia").isDisabled());c.onSegundaVia();c.onSegundaVia();assertEquals(1,i.chamadas);assertEquals(12,i.venda);
            tabela(l,"tabelaVendas").getSelectionModel().select(1);assertTrue(botao(l,"btnSegundaVia").isDisabled());i.envio.complete(null);
        });
    }
    @Test void recebimentoDeCaixaFechadoTemSegundaViaSemAlterarValorDigitado()throws Exception {
        JavafxTestSupport.executar(()->{
            FXMLLoader l=tela("FiadoList");FiadoListController c=l.getController();Imp i=new Imp();set(c,"impressaoVendaService",i);set(c,"orderService",new Vendas());
            Customer cliente=new Customer();cliente.setIdCliente(9);set(c,"customer",cliente);
            TextField campo=(TextField)l.getNamespace().get("campoValorPagamento");campo.setText("15");
            Payment p=new Payment();p.setIdRecebimento(55);p.setDataPagamento(LocalDateTime.of(2026,11,1,10,0));p.setMetodo(PaymentMethod.PIX);
            TableView<Payment> t=tabela(l,"tableRecebimentos");t.getItems().add(p);t.getSelectionModel().select(0);
            assertTrue(botao(l,"btnEstornar").isDisabled());assertFalse(botao(l,"btnSegundaViaRecebimento").isDisabled());
            c.onSegundaViaRecebimento();c.onSegundaViaRecebimento();assertEquals(1,i.chamadas);assertEquals(9,i.cliente);assertSame(p,i.recebimento);assertEquals("15",campo.getText());
            assertEquals(1,t.getItems().size());i.envio.complete(null);
        });
    }
    @Test void comprasAbertasEQuitadasSelecionamPedidoSemReceberNovamente()throws Exception {
        JavafxTestSupport.executar(()->{
            for(boolean aberta:new boolean[]{true,false}) {
                FXMLLoader l=tela("FiadoList");FiadoListController c=l.getController();Imp i=new Imp();set(c,"impressaoVendaService",i);set(c,"orderService",new Vendas());
                Order o=new Order();o.setIdPedido(aberta?21:22);o.setDataPedido(LocalDateTime.of(2026,11,1,10,0));o.setItemPedido(List.of());
                TableView<Order> t=tabela(l,aberta?"tableAbertos":"tableQuitados");t.getItems().add(o);t.getSelectionModel().select(0);
                if(aberta)c.onSegundaViaAberto();else c.onSegundaViaQuitado();assertEquals(o.getIdPedido(),i.venda);assertEquals(1,i.chamadas);i.envio.complete(null);
            }
        });
    }
    @Test void segundaViaListaNaoRecalculaNemAlteraQuantidadesEBloqueiaCliqueDuplo()throws Exception {
        final FXMLLoader[] ref={null};final Imp imp=new Imp();final int[] consultas={0};
        JavafxTestSupport.executar(()->{
            FXMLLoader l=tela("ListaCompras");ref[0]=l;ListaComprasController c=l.getController();set(c,"impressaoVendaService",imp);set(c,"executor",(Executor)Runnable::run);
            c.setService(new ListaComprasService(()->{consultas[0]++;return List.of(RegraListaCompras.acumulado(new Product("1","Arroz",BigDecimal.TEN,1),3));}));
        });
        JavafxTestSupport.executar(()->{
            ListaComprasController c=ref[0].getController();TableView<RegraListaCompras.Item> t=tabela(ref[0],"tabela");t.getItems().get(0).setComprar(99);
            c.onSegundaVia();c.onSegundaVia();assertEquals(1,imp.chamadas);assertEquals(1,consultas[0]);assertEquals(99,t.getItems().get(0).getComprar());
            assertTrue(botao(ref[0],"btnImprimir").isDisabled());imp.envio.complete(null);
        });
    }
}
