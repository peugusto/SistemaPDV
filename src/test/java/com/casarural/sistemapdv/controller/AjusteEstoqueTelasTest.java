package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.services.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class AjusteEstoqueTelasTest {
    static class Gate extends ProtecaoPin {boolean permite=true;int chamadas;public boolean autorizar(Window w,String acao){chamadas++;return permite;}}
    static class Memoria extends AjusteEstoqueService {
        final List<AjusteEstoque> chamadas=new ArrayList<>();final Map<UUID,RegistroAjusteEstoque> salvos=new LinkedHashMap<>();boolean perderResposta;
        Memoria(){super(null);}
        @Override public RegistroAjusteEstoque registrar(AjusteEstoque a){chamadas.add(a);RegistroAjusteEstoque r=salvos.get(a.operacao);if(r!=null)return r;
            r=new RegistroAjusteEstoque(a,"Arroz","COD7",a.saldoEsperado,a.saldoDepois(),0,a.reposicaoDepois(0),LocalDateTime.now());salvos.put(a.operacao,r);
            if(perderResposta){perderResposta=false;throw new ResultadoTransacaoIncertoException("registrar ajuste",new java.sql.SQLException("SINTETICO"));}return r;}
        @Override public List<RegistroAjusteEstoque> historico(int produto){return new ArrayList<>(salvos.values());}
    }
    static class Tela {
        final Product p=new Product("COD7","Arroz",BigDecimal.TEN,20);final Memoria banco=new Memoria();final Gate gate=new Gate();final FXMLLoader l;final AjusteEstoqueController c;final Stage s=new Stage();
        Tela(AjusteEstoque.Tipo tipo)throws Exception{p.setIdProduto(7);l=new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/AjusteEstoque.fxml"));Parent root=l.load();c=l.getController();SegundaViaTelasTest.set(c,"protecao",gate);c.configurar(p,tipo,banco);s.setScene(new Scene(root));}
        TextField qtd(){return (TextField)l.getNamespace().get("quantidade");}TextArea motivo(){return (TextArea)l.getNamespace().get("motivo");}Button botao(String id){return (Button)l.getNamespace().get(id);}
    }
    static void fecharAvisos(){for(Window w:new ArrayList<>(Window.getWindows()))if(w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane){DialogPane d=(DialogPane)w.getScene().getRoot();((Button)d.lookupButton(d.getButtonTypes().get(0))).fire();}}
    @Test void perdaConfereAntesDepoisExigePinESalvaUmaVez()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela(AjusteEstoque.Tipo.PERDA);t.s.show();t.qtd().setText("2");t.motivo().setText("Vencimento");assertTrue(((Label)t.l.getNamespace().get("saldoNovo")).getText().endsWith("18"));
        t.c.onSalvar();t.c.onSalvar();assertTrue(t.c.isConcluido());assertFalse(t.s.isShowing());assertEquals(1,t.gate.chamadas);assertEquals(1,t.banco.salvos.size());assertEquals(1,t.banco.chamadas.size());assertEquals(18,t.banco.chamadas.get(0).saldoDepois());assertEquals(20,t.p.getEstoque());
    });}
    @Test void contagemInformaSaldoFinalInclusiveZero()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela(AjusteEstoque.Tipo.CONTAGEM);t.qtd().setText("0");t.motivo().setText("Contagem física");t.c.onSalvar();assertTrue(t.c.isConcluido());assertEquals(0,t.banco.chamadas.get(0).saldoDepois());
    });}
    @Test void dadosInvalidosOuPinRecusadoNaoEnviamAjuste()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela(AjusteEstoque.Tipo.PERDA);t.qtd().setText("21");t.motivo().setText("Quebra");t.c.onSalvar();assertEquals(0,t.gate.chamadas);assertTrue(t.banco.chamadas.isEmpty());fecharAvisos();
        for(String invalido:List.of("1,5","2147483648","-1","abc")){t.qtd().setText(invalido);t.c.onSalvar();assertEquals(invalido,t.qtd().getText());assertEquals(0,t.gate.chamadas);assertTrue(t.banco.chamadas.isEmpty());fecharAvisos();}
        t.qtd().setText("1");t.motivo().clear();t.c.onSalvar();assertEquals(0,t.gate.chamadas);assertTrue(t.banco.chamadas.isEmpty());fecharAvisos();
        t.motivo().setText("Quebra");t.gate.permite=false;t.c.onSalvar();assertEquals(1,t.gate.chamadas);assertTrue(t.banco.chamadas.isEmpty());assertFalse(t.qtd().isDisabled());
    });}
    @Test void respostaPerdidaBloqueiaMudancasERepeteMesmaOperacaoSemNovoPin()throws Exception{JavafxTestSupport.executar(()->{
        Tela t=new Tela(AjusteEstoque.Tipo.PERDA);t.s.show();t.motivo().setText("Quebra");t.banco.perderResposta=true;t.c.onSalvar();assertFalse(t.c.isConcluido());assertTrue(t.qtd().isDisabled());assertTrue(t.motivo().isDisabled());assertTrue(t.botao("btnCancelar").isDisabled());assertEquals("Confirmar tentativa",t.botao("btnSalvar").getText());
        fecharAvisos();Event.fireEvent(t.s,new WindowEvent(t.s,WindowEvent.WINDOW_CLOSE_REQUEST));assertTrue(t.s.isShowing());fecharAvisos();t.c.onSalvar();
        assertTrue(t.c.isConcluido());assertEquals(1,t.gate.chamadas);assertEquals(1,t.banco.salvos.size());assertSame(t.banco.chamadas.get(0),t.banco.chamadas.get(1));
    });}
    @Test void cancelarNaoSalvaNemSolicitaPin()throws Exception{JavafxTestSupport.executar(()->{Tela t=new Tela(AjusteEstoque.Tipo.PERDA);t.s.show();t.c.onCancelar();assertFalse(t.s.isShowing());assertTrue(t.banco.chamadas.isEmpty());assertEquals(0,t.gate.chamadas);});}
    @Test void estoqueSemSelecaoOuSemControleNaoPermitePerdaEContagem()throws Exception{JavafxTestSupport.executar(()->{
        FXMLLoader l=new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/product_list.fxml"));Parent root=l.load();Stage s=new Stage();s.setScene(new Scene(root));
        @SuppressWarnings("unchecked") TableView<Product> tabela=(TableView<Product>)l.getNamespace().get("productTable");Button perda=(Button)l.getNamespace().get("botaoPerda"),contagem=(Button)l.getNamespace().get("botaoContagem"),historico=(Button)l.getNamespace().get("botaoHistoricoAjustes");
        assertTrue(perda.isDisabled());assertTrue(contagem.isDisabled());assertTrue(historico.isDisabled());
        Product p=new Product("7","Arroz",BigDecimal.ONE,0);p.setIdProduto(7);ProductListController c=l.getController();c.setProductService(new ProductService(null){public List<Product> findAll(){return List.of(p);}});c.updateTableView();tabela.getSelectionModel().select(0);
        assertTrue(perda.isDisabled());assertFalse(contagem.isDisabled());assertFalse(historico.isDisabled());p.setControlaEstoque(false);tabela.getSelectionModel().clearSelection();tabela.getSelectionModel().select(0);assertTrue(perda.isDisabled());assertTrue(contagem.isDisabled());assertFalse(historico.isDisabled());
    });}
    @Test void historicoMostraMotivoESaldosSemRegistrarNovoAjuste()throws Exception{JavafxTestSupport.executar(()->{
        FXMLLoader l=new FXMLLoader(getClass().getResource("/com/casarural/sistemapdv/view/product_list.fxml"));Parent root=l.load();Stage s=new Stage();s.setScene(new Scene(root));
        Product p=new Product("COD7","Arroz",BigDecimal.TEN,18);p.setIdProduto(7);Memoria banco=new Memoria();banco.registrar(new AjusteEstoque(UUID.randomUUID(),7,AjusteEstoque.Tipo.PERDA,20,2,"Embalagens danificadas"));
        ProductListController c=l.getController();SegundaViaTelasTest.set(c,"ajustes",banco);Gate g=new Gate();g.permite=false;SegundaViaTelasTest.set(c,"protecao",g);c.setProductService(new ProductService(null){public List<Product> findAll(){return List.of(p);}});c.updateTableView();
        @SuppressWarnings("unchecked") TableView<Product> produtos=(TableView<Product>)l.getNamespace().get("productTable");produtos.getSelectionModel().select(0);
        Platform.runLater(()->{
            Window w=Window.getWindows().stream().filter(j->j.isShowing()&&j.getScene()!=null&&j.getScene().lookup("#historicoAjustes")!=null).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") TableView<RegistroAjusteEstoque> tabela=(TableView<RegistroAjusteEstoque>)w.getScene().lookup("#historicoAjustes");
            assertEquals(1,tabela.getItems().size());RegistroAjusteEstoque r=tabela.getItems().get(0);assertEquals(20,r.antes);assertEquals(18,r.depois);assertEquals("Embalagens danificadas",r.ajuste.motivo);
            w.getScene().getRoot().lookupAll(".button").stream().filter(n->n instanceof Button&&((Button)n).getText().equals("Fechar")).map(n->(Button)n).findFirst().orElseThrow().fire();
        });c.onHistoricoAjustes();assertEquals(1,banco.chamadas.size());assertEquals(0,g.chamadas);
    });}
}
