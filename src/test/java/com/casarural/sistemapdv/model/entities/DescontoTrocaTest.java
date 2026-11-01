package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DescontoTrocaTest {
    static OrderItem item(int id,int qtd,String preco) {
        Product p=new Product(""+id,"Produto "+id,Money.of(new BigDecimal(preco)),100);p.setIdProduto(id);
        return new OrderItem(p,qtd,new BigDecimal(preco));
    }
    static VendaParaDesfazer original() {
        VendaParaDesfazer v=new VendaParaDesfazer();v.setDesconto(new BigDecimal("10"));
        v.getItens().addAll(List.of(item(1,2,"25"),item(2,1,"50")));return v;
    }
    @Test void mesmaCompraMantemDescontoMesmoReordenadaOuDivididaEmLinhas() {
        VendaParaDesfazer v=original();
        assertEquals(Money.parse("10"),RegraTroca.descontoOriginal(v,List.of(item(2,1,"50"),item(1,1,"25"),item(1,1,"25.00"))));
        assertEquals(2,v.getItens().get(0).getQtd());assertEquals(Money.parse("10"),v.getDesconto());
    }
    @Test void mudarQuantidadePrecoOuProdutoRetiraDesconto() {
        for(List<OrderItem> itens:List.of(
                List.of(item(1,1,"25"),item(2,1,"50")),
                List.of(item(1,2,"25"),item(2,1,"51")),
                List.of(item(3,2,"25"),item(2,1,"50")),List.<OrderItem>of()))
            assertEquals(Money.ZERO,RegraTroca.descontoOriginal(original(),itens));
    }
    @Test void vendaNormalOuOriginalSemDescontoNaoRecebeDesconto() {
        assertEquals(Money.ZERO,RegraTroca.descontoOriginal(null,List.of(item(1,1,"100"))));
        VendaParaDesfazer v=original();v.setDesconto(Money.ZERO);
        assertEquals(Money.ZERO,RegraTroca.descontoOriginal(v,v.getItens()));
    }
}
