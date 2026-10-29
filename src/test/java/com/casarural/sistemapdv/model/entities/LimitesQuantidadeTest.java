package com.casarural.sistemapdv.model.entities;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class LimitesQuantidadeTest {
    @Test void estoqueNaoTransbordaNemMudaAposFalha(){Product p=new Product("1","Teste",BigDecimal.ONE,10);assertThrows(IllegalArgumentException.class,()->p.adicionarEstoque(Integer.MAX_VALUE));assertEquals(10,p.getEstoque());assertThrows(IllegalArgumentException.class,()->p.adicionarEstoque(-1));assertEquals(10,p.getEstoque());}
    @Test void itemNaoAceitaQuantidadeNegativaOuZero(){Product p=new Product();assertThrows(IllegalArgumentException.class,()->new OrderItem(p,-1,BigDecimal.ONE));OrderItem i=new OrderItem(p,1,BigDecimal.ONE);assertThrows(IllegalArgumentException.class,()->i.setQtd(0));assertEquals(1,i.getQtd());}
    @Test void valorNegativoNaoPodeZerarVendaPorDesconto(){OrderItem i=new OrderItem(new Product(),1,BigDecimal.ONE);i.setPrecoUnitario(BigDecimal.ONE.negate());Order o=new Order();o.setItemPedido(java.util.List.of(i));assertThrows(IllegalStateException.class,()->o.aplicarDesconto(BigDecimal.ZERO));assertEquals(BigDecimal.ZERO.setScale(2),o.getDesconto());}
    @Test void recomendacaoNaoTransbordaNoLimite(){assertEquals(0,RegraListaCompras.quantoComprar(Integer.MIN_VALUE,Integer.MAX_VALUE));assertEquals(Integer.MAX_VALUE,RegraListaCompras.quantoComprar(Integer.MAX_VALUE,0));}
}
