package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.VendaSuspensa.*;
import com.casarural.sistemapdv.services.*;
import com.casarural.sistemapdv.util.Money;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class VendasSuspensasTelasTest {
    static class Persistencia extends VendaSuspensaService {
        final Map<UUID,VendaSuspensa> vendas=new LinkedHashMap<>();final Map<UUID,UUID> ops=new HashMap<>();final List<Gravacao> chamadas=new ArrayList<>();
        final Product produto;boolean falharDepois;int retomadas;
        Persistencia(Product p){super(null);produto=p;}
        @Override public List<VendaSuspensa> listar(){return vendas.values().stream().filter(v->v.estado==Estado.SUSPENSA).collect(java.util.stream.Collectors.toList());}
        @Override public VendaSuspensa gravar(Gravacao g){
            chamadas.add(g);if(g.operacao.equals(ops.get(g.id)))return vendas.get(g.id);
            VendaSuspensa v=new VendaSuspensa(g.id,g.identificacao,g.versao+1,g.destino,g.destino==Estado.SUSPENSA?null:"SESSAO_FICTICIA",g.token,LocalDateTime.now(),LocalDateTime.now(),null,g.itens);
            vendas.put(g.id,v);ops.put(g.id,g.operacao);
            if(falharDepois){falharDepois=false;throw new ResultadoTransacaoIncertoException("salvar carrinho",new java.sql.SQLException("SINTETICO"));}return v;
        }
        @Override public RetomadaVenda preparar(UUID id){return new RetomadaVenda(vendas.get(id),Map.of(produto.getIdProduto(),produto));}
        @Override public VendaSuspensa retomar(RetomadaVenda p,UUID op,UUID token,boolean c){retomadas++;VendaSuspensa v=emUso(p.original,token);vendas.put(v.id,v);return v;}
        VendaSuspensa emUso(VendaSuspensa v,UUID token){return new VendaSuspensa(v.id,v.identificacao,v.versao+1,Estado.EM_USO,"SESSAO_FICTICIA",token.toString(),v.criadaEm,LocalDateTime.now(),null,v.itens);}
        VendaSuspensa prepararCarrinho(){VendaSuspensa v=new VendaSuspensa(UUID.randomUUID(),"Maria",1,Estado.SUSPENSA,null,null,LocalDateTime.now(),LocalDateTime.now(),null,VendaSuspensa.capturar(List.of(new OrderItem(produto,1,produto.getPrecoProduto()))));vendas.put(v.id,v);return v;}
    }
    static class Pagamento extends PaymentController {
        boolean lancou,concluiu;
        @Override public boolean isHouveLancamento(){return lancou;}
        @Override public boolean isVendaConcluida(){return concluiu;}
    }
    static class Pdv extends PDVController {
        Pagamento pagamento=new Pagamento();int abriu,continuou;Order order;
        @Override protected PaymentController abrirPagamento(Order o){abriu++;order=o;return pagamento;}
        @Override protected void continuarPagamento(PaymentController p){assertSame(pagamento,p);continuou++;pagamento.concluiu=true;}
    }
    static class Tela {
        final Product p=new Product("COD7","Arroz",Money.parse("2,50"),20);final Persistencia banco;final FXMLLoader l;final Pdv c;final Stage stage;
        int consultas;
        Tela()throws Exception {
            p.setIdProduto(7);banco=new Persistencia(p);l=new FXMLLoader(PDVController.class.getResource("/com/casarural/sistemapdv/view/pdv.fxml"));l.setControllerFactory(t->new Pdv());Parent raiz=l.load();stage=new Stage();stage.setScene(new Scene(raiz));c=l.getController();
            ProductDao dao=(ProductDao)Proxy.newProxyInstance(ProductDao.class.getClassLoader(),new Class<?>[]{ProductDao.class},(x,m,a)->{
                if(m.getName().equals("findByCodBarras")){consultas++;return Optional.of(p);}if(m.getName().equals("findAll"))return List.of(p);throw new AssertionError(m.getName());
            });c.setProductService(new ProductService(dao));c.setVendaSuspensaService(banco);
        }
        Button b(String id){return (Button)l.getNamespace().get(id);}TextField f(String id){return (TextField)l.getNamespace().get(id);}
        @SuppressWarnings("unchecked") ListView<OrderItem> lista(){return (ListView<OrderItem>)l.getNamespace().get("listaItens");}
        void adicionar(String qtd){f("campoCodigoProduto").setText("COD7");f("campoQuantidade").setText(qtd);b("botaoAdicionar").fire();}
        void carregar(){VendaSuspensa v=banco.prepararCarrinho();VendaSuspensa atual=banco.emUso(v,UUID.randomUUID());banco.vendas.put(atual.id,atual);c.carregarRetomada(new VendasSuspensasDialogo.Resultado(atual,List.of(new OrderItem(p,1,p.getPrecoProduto()))));}
    }
    static DialogPane dialogo(){return Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElseThrow();}
    static void responder(String nome){Platform.runLater(()->{DialogPane d=dialogo();if(nome!=null)((TextField)d.lookup(".text-field")).setText(nome);ButtonType ok=d.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElse(d.getButtonTypes().get(0));((Button)d.lookupButton(ok)).fire();});}
    static void fecharAviso(){DialogPane d=dialogo();((Button)d.lookupButton(d.getButtonTypes().get(0))).fire();}
    @Test void suspendeLimpaSomenteDepoisDeConfirmarERetomaPelaLista()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();assertTrue(t.b("botaoRetomar").isDisabled());t.c.onRetomarVenda();t.adicionar("2");responder("Maria");t.b("botaoSuspender").fire();assertTrue(t.lista().getItems().isEmpty());assertEquals(1,t.banco.vendas.size());assertTrue(t.b("botaoRetomar").getText().contains("(1)"));assertFalse(t.b("botaoRetomar").isDisabled());
        Platform.runLater(()->{
            Window janela=Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#tabelaSuspensas")!=null).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") TableView<VendaSuspensa> tab=(TableView<VendaSuspensa>)janela.getScene().lookup("#tabelaSuspensas");tab.getSelectionModel().select(0);((Button)janela.getScene().lookup("#retomarSuspensa")).fire();
        });t.b("botaoRetomar").fire();assertEquals(1,t.banco.retomadas);assertEquals(2,t.lista().getItems().get(0).getQtd());assertTrue(t.b("botaoRetomar").getText().contains("(0)"));assertTrue(t.b("botaoRetomar").isDisabled());
    });}
    @Test void respostaPerdidaNaoLimpaNemDuplicaCarrinhoNaRepeticao()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.adicionar("1");t.banco.falharDepois=true;responder("João");t.b("botaoSuspender").fire();
        assertEquals(1,t.lista().getItems().size());assertTrue(t.b("botaoFinalizar").isDisabled());assertTrue(t.f("campoCodigoProduto").isDisabled());assertEquals(1,t.banco.vendas.size());
        fecharAviso();t.c.onSuspenderVenda();assertTrue(t.lista().getItems().isEmpty());assertEquals(1,t.banco.vendas.size());assertEquals(t.banco.chamadas.get(0).operacao,t.banco.chamadas.get(1).operacao);
    });}
    @Test void carrinhoRetomadoSalvaEdicoesSemConsultaPeriodica()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.carregar();assertEquals(0,t.banco.chamadas.size());t.adicionar("1");assertEquals(2,t.lista().getItems().get(0).getQtd());assertEquals(1,t.banco.chamadas.size());assertEquals(Estado.EM_USO,t.banco.chamadas.get(0).destino);
        t.lista().getSelectionModel().select(0);t.b("botaoRemover").fire();assertTrue(t.lista().getItems().isEmpty());assertEquals(2,t.banco.chamadas.size());
    });}
    @Test void edicaoPendenteImpedeNovasMudancasEConfirmaMesmaOperacao()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.carregar();t.banco.falharDepois=true;t.adicionar("1");assertEquals(2,t.lista().getItems().get(0).getQtd());assertTrue(t.b("botaoAdicionar").isDisabled());
        fecharAviso();t.c.onSuspenderVenda();assertFalse(t.b("botaoAdicionar").isDisabled());assertEquals(t.banco.chamadas.get(0).operacao,t.banco.chamadas.get(1).operacao);assertEquals(2,t.lista().getItems().get(0).getQtd());
    });}
    @Test void fecharTelaRetornadaRessuspendeSemApagarItens()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.stage.show();t.carregar();Event.fireEvent(t.stage,new WindowEvent(t.stage,WindowEvent.WINDOW_CLOSE_REQUEST));
        assertEquals(Estado.SUSPENSA,t.banco.vendas.values().iterator().next().estado);assertEquals(1,t.banco.vendas.values().iterator().next().itens.size());
    });}
    @Test void escapeComCarrinhoVazioNaoBypassaGravacaoPendenteENemDeixaRascunhoPreso()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.stage.show();t.carregar();t.stage.getScene().addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)t.stage.close();});
        t.banco.falharDepois=true;t.lista().getSelectionModel().select(0);t.b("botaoRemover").fire();fecharAviso();
        Event.fireEvent(t.stage.getScene(),new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ESCAPE,false,false,false,false));
        assertTrue(t.stage.isShowing());assertEquals(1,t.banco.chamadas.size());t.c.onSuspenderVenda();
        responder(null);Event.fireEvent(t.stage.getScene(),new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ESCAPE,false,false,false,false));
        assertEquals(Estado.DESCARTADA,t.banco.vendas.values().iterator().next().estado);assertTrue(t.stage.isShowing());
    });}
    @Test void pagamentoLancadoEhRetidoESuspensaoFicaBloqueada()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.carregar();t.c.pagamento.lancou=true;t.b("botaoFinalizar").fire();assertEquals(1,t.c.abriu);assertEquals(Estado.EM_PAGAMENTO,t.c.order.getVendaSuspensa().estado);
        assertTrue(t.b("botaoSuspender").isDisabled());assertTrue(t.b("botaoAdicionar").isDisabled());assertEquals("Continuar pagamento (F1)",t.b("botaoFinalizar").getText());
        t.b("botaoFinalizar").fire();assertEquals(1,t.c.abriu);assertEquals(1,t.c.continuou);assertTrue(t.lista().getItems().isEmpty());
    });}
    @Test void voltarAntesDeLancarPagamentoLiberaEdicaoESuspensao()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.carregar();t.b("botaoFinalizar").fire();assertFalse(t.b("botaoSuspender").isDisabled());assertFalse(t.b("botaoAdicionar").isDisabled());
        assertEquals(List.of(Estado.EM_PAGAMENTO,Estado.EM_USO),t.banco.chamadas.stream().map(g->g.destino).collect(java.util.stream.Collectors.toList()));
    });}
    @Test void respostaPerdidaAoAbrirPagamentoPodeSerConfirmadaSemBloquearContinuacao()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.carregar();t.banco.falharDepois=true;t.b("botaoFinalizar").fire();assertEquals(0,t.c.abriu);assertTrue(t.b("botaoFinalizar").isDisabled());
        fecharAviso();t.c.onSuspenderVenda();assertFalse(t.b("botaoFinalizar").isDisabled());assertTrue(t.b("botaoAdicionar").isDisabled());
        Event.fireEvent(t.stage.getScene(),new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",javafx.scene.input.KeyCode.ESCAPE,false,false,false,false));
        assertEquals(2,t.banco.chamadas.size());assertFalse(t.b("botaoFinalizar").isDisabled());fecharAviso();
        t.b("botaoFinalizar").fire();assertEquals(1,t.c.abriu);assertFalse(t.b("botaoAdicionar").isDisabled());assertEquals(1,t.banco.vendas.size());
        assertEquals(t.banco.chamadas.get(0).operacao,t.banco.chamadas.get(1).operacao);
    });}
    @Test void cancelarConferenciaDePrecoNaoReivindicaNemAlteraCarrinho()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.banco.prepararCarrinho();t.p.setPrecoProduto(Money.parse("3"));t.c.setVendaSuspensaService(t.banco);
        Platform.runLater(()->{
            Window janela=Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#tabelaSuspensas")!=null).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") TableView<VendaSuspensa> tab=(TableView<VendaSuspensa>)janela.getScene().lookup("#tabelaSuspensas");tab.getSelectionModel().select(0);
            Platform.runLater(()->{DialogPane d=dialogo();((Button)d.lookupButton(ButtonType.CANCEL)).fire();Platform.runLater(()->((Button)janela.getScene().lookup("#fecharSuspensas")).fire());});
            ((Button)janela.getScene().lookup("#retomarSuspensa")).fire();
        });t.b("botaoRetomar").fire();assertTrue(t.lista().getItems().isEmpty());assertEquals(0,t.banco.retomadas);assertEquals(Estado.SUSPENSA,t.banco.vendas.values().iterator().next().estado);
    });}
    @Test void alteracaoForaDoLimiteVoltaAoUltimoCarrinhoSalvo()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();t.p.setEstoque(Integer.MAX_VALUE);t.carregar();t.adicionar("100000000");assertEquals(1,t.lista().getItems().get(0).getQtd());assertEquals(0,t.banco.chamadas.size());assertFalse(t.b("botaoAdicionar").isDisabled());
    });}
    @Test void botoesNovosCabemNaJanelaSemSairDaBarra()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela();Parent root=t.stage.getScene().getRoot();root.resize(1100,760);root.applyCss();root.layout();
        for(String id:List.of("botaoSuspender","botaoRetomar","botaoFinalizar")){Button b=t.b(id);assertTrue(b.localToScene(b.getBoundsInLocal()).getMaxX()<=1101,id+" saiu da janela");assertTrue(b.getBoundsInParent().getMinY()>=0);}
    });}
}
