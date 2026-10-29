package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.services.*;
import com.casarural.sistemapdv.util.Money;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class ImpressaoTelasTest {
    static void set(Object o,String nome,Object valor) throws Exception {Field f=o.getClass().getDeclaredField(nome); f.setAccessible(true); f.set(o,valor);}
    static FXMLLoader tela(String nome) throws Exception {
        FXMLLoader l=new FXMLLoader(ImpressaoTelasTest.class.getResource("/com/casarural/sistemapdv/view/"+nome+".fxml"));
        Parent root=l.load(); Stage s=new Stage(); s.setScene(new Scene(root)); com.casarural.sistemapdv.util.Tema.aplicar(s.getScene()); return l;
    }
    static CashRegister caixa() {CashRegister c=new CashRegister(); c.setIdCaixa(11); c.setAbertoEm(LocalDateTime.of(2026,10,29,8,0)); return c;}
    static class CaixaFake extends CashRegisterService {
        int fechamentos; boolean falha;
        CaixaFake(){super(null);}
        public CashSummary summarize(CashRegister c){return new CashSummary(Money.ZERO);}
        public List<CashMovement> findMovements(CashRegister c){return List.of();}
        public CashRegister close(CashRegister c,BigDecimal valor,String obs){fechamentos++; if(falha)throw new DbException("Falha simulada antes de gravar"); return c;}
        public List<CashRegister> findClosed(int limite){CashRegister c=caixa(); c.setFechadoEm(c.getAbertoEm().plusHours(10)); return List.of(c);}
    }
    static class ImpressaoFake extends ImpressaoVendaService {
        final CompletableFuture<Void> trabalho=new CompletableFuture<>(); int chamadas,cliente,caixa; CaixaFake caixaService;
        ImpressaoFake(){super(c -> {},Runnable::run);}
        public CompletableFuture<Void> emitirFechamento(int id){if(caixaService!=null)assertEquals(1,caixaService.fechamentos); chamadas++;caixa=id;return trabalho;}
        public CompletableFuture<Void> emitirDivida(int id){chamadas++;cliente=id;return trabalho;}
    }
    static void confirmar(boolean sim) {
        Platform.runLater(() -> {
            for(Window w:new ArrayList<>(Window.getWindows()))if(w.getScene()!=null && w.getScene().getRoot() instanceof DialogPane){
                DialogPane d=(DialogPane)w.getScene().getRoot();
                ButtonType b=d.getButtonTypes().stream().filter(t -> t.getButtonData()==(sim?ButtonBar.ButtonData.OK_DONE:ButtonBar.ButtonData.CANCEL_CLOSE)).findFirst().orElseThrow();
                ((Button)d.lookupButton(b)).fire();return;
            }
            throw new AssertionError("Confirmação não encontrada");
        });
    }
    @Test void fechamentoConfirmadoSolicitaUmaImpressaoSemRepetirFechamento() throws Exception {JavafxTestSupport.executar(() -> {
        FXMLLoader l=tela("FecharCaixa"); FecharCaixaController c=l.getController(); CaixaFake serv=new CaixaFake();
        ImpressaoFake imp=new ImpressaoFake(); imp.caixaService=serv; set(c,"impressaoVendaService",imp); c.configurar(serv,caixa());
        confirmar(true); c.onFecharCaixa(); c.onFecharCaixa();
        assertTrue(c.isConcluido()); assertEquals(1,serv.fechamentos); assertEquals(1,imp.chamadas); assertEquals(11,imp.caixa);
        imp.trabalho.complete(null);
    });}
    @Test void fechamentoRecusadoNaoImprimeENaoMarcaConcluido() throws Exception {JavafxTestSupport.executar(() -> {
        FXMLLoader l=tela("FecharCaixa"); FecharCaixaController c=l.getController(); CaixaFake serv=new CaixaFake(); serv.falha=true;
        ImpressaoFake imp=new ImpressaoFake(); set(c,"impressaoVendaService",imp); c.configurar(serv,caixa());
        confirmar(true); c.onFecharCaixa(); assertFalse(c.isConcluido()); assertEquals(0,imp.chamadas);
        assertFalse(((Button)l.getNamespace().get("btnFechar")).isDisabled());
    });}
    @Test void cancelarConfirmacaoNaoFechaNemImprime() throws Exception {JavafxTestSupport.executar(() -> {
        FXMLLoader l=tela("FecharCaixa"); FecharCaixaController c=l.getController(); CaixaFake serv=new CaixaFake();
        ImpressaoFake imp=new ImpressaoFake(); set(c,"impressaoVendaService",imp); c.configurar(serv,caixa());
        confirmar(false); c.onFecharCaixa(); assertEquals(0,serv.fechamentos); assertEquals(0,imp.chamadas);
    });}
    @Test void falhaDaImpressoraNaoDesfazFechamentoConfirmado() throws Exception {
        final FecharCaixaController[] ref={null}; final CaixaFake[] servRef={null};
        JavafxTestSupport.executar(() -> {
            FXMLLoader l=tela("FecharCaixa"); FecharCaixaController c=l.getController(); CaixaFake serv=new CaixaFake();
            ImpressaoFake imp=new ImpressaoFake(); set(c,"impressaoVendaService",imp); c.configurar(serv,caixa());
            confirmar(true); c.onFecharCaixa(); imp.trabalho.completeExceptionally(new IllegalStateException("Sem papel")); ref[0]=c; servRef[0]=serv;
        });
        JavafxTestSupport.executar(() -> {assertTrue(ref[0].isConcluido()); assertEquals(1,servRef[0].fechamentos);});
    }
    @Test void botaoDaDividaNaoRecebePagamentoEImpedeCliqueDuplicado() throws Exception {
        final ImpressaoFake imp=new ImpressaoFake(); final FXMLLoader[] ref={null};
        JavafxTestSupport.executar(() -> {
            FXMLLoader l=tela("FiadoList"); ref[0]=l; FiadoListController c=l.getController(); Customer cliente=new Customer(); cliente.setIdCliente(7);
            set(c,"customer",cliente); set(c,"impressaoVendaService",imp);
            TextField campo=(TextField)l.getNamespace().get("campoValorPagamento"); campo.setText("15");
            Button imprimir=(Button)l.getNamespace().get("btnImprimirDivida"); imprimir.fire(); c.onImprimirDividaAction();
            assertEquals(1,imp.chamadas); assertEquals(7,imp.cliente); assertEquals("15",campo.getText()); assertTrue(imprimir.isDisabled());
            imp.trabalho.complete(null);
        });
        JavafxTestSupport.executar(() -> assertFalse(((Button)ref[0].getNamespace().get("btnImprimirDivida")).isDisabled()));
    }
    @Test void rodapeDoFiadoMantemBotoesLadoALadoSemCortarNaJanela() throws Exception {JavafxTestSupport.executar(() -> {
        FXMLLoader l=tela("FiadoList"); Button receber=(Button)l.getNamespace().get("btnPagar"), imprimir=(Button)l.getNamespace().get("btnImprimirDivida");
        Parent root=receber.getScene().getRoot(); root.resize(1060,720); root.applyCss(); root.layout();
        assertSame(receber.getParent(),imprimir.getParent()); assertTrue(imprimir.getBoundsInParent().getMaxX()<=imprimir.getParent().getLayoutBounds().getWidth()+1);
        assertTrue(imprimir.localToScene(imprimir.getBoundsInLocal()).getMaxY()<=720, "Rodapé em y="+imprimir.localToScene(imprimir.getBoundsInLocal()).getMaxY());
    });}
    @Test void historicoImprimeSomenteCaixaSelecionadoSemFecharDeNovo() throws Exception {JavafxTestSupport.executar(() -> {
        FXMLLoader l=tela("HistoricoCaixa"); HistoricoCaixaController c=l.getController(); CaixaFake serv=new CaixaFake(); ImpressaoFake imp=new ImpressaoFake();
        set(c,"impressaoVendaService",imp); c.configurar(serv); c.onImprimirAction(); assertEquals(0,imp.chamadas);
        @SuppressWarnings("unchecked") TableView<CashRegister> tabela=(TableView<CashRegister>)l.getNamespace().get("tabela");
        tabela.getSelectionModel().select(0); c.onImprimirAction(); c.onImprimirAction();
        assertEquals(1,imp.chamadas); assertEquals(11,imp.caixa); assertEquals(0,serv.fechamentos); imp.trabalho.complete(null);
    });}
}
