package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(JavafxTestSupport.class)
class DevolucaoHistoricoTelasTest {
    static VendaParaDesfazer venda(){
        VendaParaDesfazer v=new VendaParaDesfazer();v.setIdPedido(12);v.setStatus(OrderStatus.PAGO);v.setIdCaixaOriginal(1);v.setCaixaAtualDisponivel(true);
        Product p=new Product();OrderItem i=new OrderItem(p,1,BigDecimal.TEN);i.setEstoqueBaixado(1);v.getItens().add(i);return v;
    }
    static void mostrar(FXMLLoader l,VendaParaDesfazer v)throws Exception{
        OrderListController c=l.getController();SegundaViaTelasTest.set(c,"vendaDetalhe",v);
        java.lang.reflect.Method m=OrderListController.class.getDeclaredMethod("mostrarAcoes");m.setAccessible(true);m.invoke(c);
    }
    @Test void vendaDeOutroCaixaOfereceDevolucaoETrocaExplicandoOndeSeraLancada()throws Exception{JavafxTestSupport.executar(()->{
        FXMLLoader l=SegundaViaTelasTest.tela("order-list");mostrar(l,venda());
        Button devolver=SegundaViaTelasTest.botao(l,"btnCancelarVenda");assertTrue(devolver.isVisible());assertEquals("Devolver venda",devolver.getText());
        assertTrue(SegundaViaTelasTest.botao(l,"btnTrocar").isVisible());assertTrue(((Label)l.getNamespace().get("labelAcoesDica")).getText().contains("preservado"));
        VendaParaDesfazer atual=venda();atual.setDoCaixaAberto(true);mostrar(l,atual);assertEquals("Cancelar venda",devolver.getText());
    });}
    @Test void semCaixaOuOrigemConhecidaOuVendaJaDesfeitaNaoPermiteRepetir()throws Exception{JavafxTestSupport.executar(()->{
        FXMLLoader l=SegundaViaTelasTest.tela("order-list");
        for(int caso=0;caso<4;caso++){
            VendaParaDesfazer v=venda();if(caso==0)v.setCaixaAtualDisponivel(false);if(caso==1)v.setIdCaixaOriginal(null);if(caso==2)v.getItens().get(0).setEstoqueBaixado(null);if(caso==3)v.setStatus(OrderStatus.CANCELADO);
            mostrar(l,v);assertFalse(SegundaViaTelasTest.botao(l,"btnCancelarVenda").isVisible());assertFalse(SegundaViaTelasTest.botao(l,"btnTrocar").isVisible());
        }
    });}
}
