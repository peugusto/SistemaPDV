package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import com.casarural.sistemapdv.services.*;
import com.casarural.sistemapdv.util.Money;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class ListaComprasAtualizacaoTest {
    static Object campo(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static void chamar(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    static Item item(String nome,int estoque,int qtd){return RegraListaCompras.acumulado(new Product(nome,nome,Money.parse("10"),estoque),qtd);}
    static class Tela {
        final FXMLLoader f; final ListaComprasController c; final List<Runnable> impressao=new ArrayList<>();
        Tela()throws Exception{
            f=new FXMLLoader(ListaComprasController.class.getResource("/com/casarural/sistemapdv/view/ListaCompras.fxml"));
            Parent root=f.load(); Stage stage=new Stage();stage.setScene(new Scene(root));com.casarural.sistemapdv.util.Tema.aplicar(stage.getScene());c=f.getController();
            set(c,"executor",(Executor)Runnable::run);set(c,"impressaoVendaService",new ImpressaoVendaService(doc->{},impressao::add));
        }
        Button botao(String n){return (Button)f.getNamespace().get(n);}
        @SuppressWarnings("unchecked") List<Item> itens(){return ((TableView<Item>)f.getNamespace().get("tabela")).getItems();}
    }
    @Test void consultaNaoBloqueiaTelaEImpressaoEsperaDadosValidos()throws Exception{
        Tela[] t={null}; List<Runnable> carga=new ArrayList<>();
        JavafxTestSupport.executar(()->{t[0]=new Tela();set(t[0].c,"executor",(Executor)carga::add);t[0].c.setService(new ListaComprasService(()->List.of(item("Arroz",1,3))));
            t[0].c.onImprimir();assertTrue(t[0].impressao.isEmpty());assertTrue(t[0].botao("btnImprimir").isDisabled());assertEquals(1,carga.size());carga.get(0).run();});
        JavafxTestSupport.executar(()->{assertEquals(1,t[0].itens().size());assertFalse(t[0].botao("btnImprimir").isDisabled());});
    }
    @Test void cliqueDuplicadoNaoEnfileiraDuasCopias()throws Exception{
        Tela[] t={null};JavafxTestSupport.executar(()->{t[0]=new Tela();t[0].c.setService(new ListaComprasService(()->List.of(item("Arroz",1,3))));});
        JavafxTestSupport.executar(()->{t[0].c.onImprimir();t[0].c.onImprimir();assertEquals(1,t[0].impressao.size());assertTrue(t[0].botao("btnImprimir").isDisabled());t[0].impressao.get(0).run();});
        JavafxTestSupport.executar(()->{assertFalse(t[0].botao("btnImprimir").isDisabled());assertEquals(3,t[0].itens().get(0).getComprar());});
    }
    @Test void quantidadeZeroSaiTambemDoIndicadorDeAcabados()throws Exception{
        Tela[] t={null};JavafxTestSupport.executar(()->{t[0]=new Tela();t[0].c.setService(new ListaComprasService(()->List.of(item("Arroz",0,3))));});
        JavafxTestSupport.executar(()->{t[0].itens().get(0).setComprar(0);chamar(t[0].c,"atualizarResumo");
            assertEquals("0",((Label)t[0].f.getNamespace().get("lblAcabaram")).getText());assertTrue(t[0].botao("btnImprimir").isDisabled());});
    }
    @Test void falhaDeAtualizacaoBloqueiaImpressaoDaListaAntiga()throws Exception{
        Tela[] t={null};boolean[] falha={false};JavafxTestSupport.executar(()->{t[0]=new Tela();t[0].c.setService(new ListaComprasService(()->{if(falha[0])throw new IllegalStateException("Falha simulada");return List.of(item("Arroz",1,3));}));});
        JavafxTestSupport.executar(()->{falha[0]=true;t[0].c.onAtualizar();});
        JavafxTestSupport.executar(()->{assertTrue(t[0].botao("btnImprimir").isDisabled());t[0].c.onImprimir();assertTrue(t[0].impressao.isEmpty());
            assertTrue(((Label)t[0].f.getNamespace().get("lblCopiado")).getText().contains("sem atualização"));});
    }
    @Test void editarEImprimirNaoConsultamNovamenteAtualizarRecarrega()throws Exception{
        Tela[] t={null};int[] consultas={0};
        JavafxTestSupport.executar(()->{t[0]=new Tela();t[0].c.setService(new ListaComprasService(()->{consultas[0]++;return List.of(item("Arroz",1,3));}));});
        JavafxTestSupport.executar(()->{
            t[0].itens().get(0).setComprar(8);t[0].c.onImprimir();
            assertEquals(1,consultas[0]);assertEquals(8,t[0].itens().get(0).getComprar());t[0].impressao.get(0).run();
        });
        JavafxTestSupport.executar(()->{assertEquals(1,consultas[0]);t[0].c.onAtualizar();});
        JavafxTestSupport.executar(()->{assertEquals(2,consultas[0]);assertEquals(3,t[0].itens().get(0).getComprar());});
    }
    @Test void botoesCabemNaJanelaEAtualizacaoNaoPerdeQuantidadeNova()throws Exception{
        Tela[] t={null};int[] q={3};JavafxTestSupport.executar(()->{t[0]=new Tela();t[0].c.setService(new ListaComprasService(()->List.of(item("Arroz",1,q[0]))));});
        JavafxTestSupport.executar(()->{q[0]=4;t[0].c.onAtualizar();});
        JavafxTestSupport.executar(()->{assertEquals(4,t[0].itens().get(0).getComprar());Button imprimir=t[0].botao("btnImprimir");Parent root=imprimir.getScene().getRoot();root.resize(1000,720);root.applyCss();root.layout();
            assertTrue(imprimir.localToScene(imprimir.getBoundsInLocal()).getMaxX()<=1000);assertTrue(imprimir.localToScene(imprimir.getBoundsInLocal()).getMaxY()<=720);});
    }
}
