package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.VendaSuspensa.*;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RetomadaVendaTest {
    static BigDecimal r(String s){return new BigDecimal(s).setScale(2);}
    static Product produto(int id,String preco,int estoque){Product p=new Product("COD"+id,"Produto "+id,r(preco),estoque);p.setIdProduto(id);return p;}
    static VendaSuspensa venda(Linha...linhas){return new VendaSuspensa(UUID.randomUUID(),"Maria",1,Estado.SUSPENSA,null,null,LocalDateTime.now(),LocalDateTime.now(),null,List.of(linhas));}
    static Linha linha(Product p,int qtd,String valor){return Linha.de(new OrderItem(p,qtd,r(valor)));}
    @Test void precoAtualEEstoqueSaoConferidosSemMudarSnapshotOriginal(){
        Product antigo=produto(1,"10",100);VendaSuspensa v=venda(linha(antigo,5,"10"));Product atual=produto(1,"12",3);
        RetomadaVenda p=new RetomadaVenda(v,Map.of(1,atual));assertEquals(1,p.itens.size());assertEquals(3,p.itens.get(0).getQtd());assertEquals(r("36"),p.itens.get(0).getSubtotal());
        assertEquals(r("50"),v.getTotal());assertEquals(5,v.itens.get(0).quantidade);assertEquals(2,p.avisos.size());
    }
    @Test void somaEstoqueDeLinhasRepetidasNaoAutorizaUnidadesExtras(){
        Product p=produto(1,"10",5);RetomadaVenda r=new RetomadaVenda(venda(linha(p,4,"10"),linha(p,4,"10")),Map.of(1,p));
        assertEquals(5,r.itens.stream().mapToInt(OrderItem::getQtd).sum());assertEquals(1,r.itens.get(1).getQtd());
    }
    @Test void excluidoOuInativoNaoVoltaAoCarrinhoMesmoSeCodigoForReutilizado(){
        Product p=produto(1,"10",5),inativo=produto(2,"20",5);inativo.setAtivo(false);Product outro=produto(3,"99",5);outro.setCodBarras(p.getCodBarras());
        RetomadaVenda r=new RetomadaVenda(venda(linha(p,1,"10"),linha(inativo,1,"20")),Map.of(2,inativo,3,outro));
        assertTrue(r.itens.isEmpty());assertEquals(2,r.avisos.size());
    }
    @Test void pesagemPreservaValorDigitadoSomenteComMesmoPrecoPorPeso(){
        Product p=produto(1,"50",0);p.setTipoVenda(TipoVenda.PESAVEL);VendaSuspensa v=venda(linha(p,1,"12.35"));
        assertEquals(r("12.35"),new RetomadaVenda(v,Map.of(1,p)).itens.get(0).getSubtotal());
        Product novo=produto(1,"60",0);novo.setTipoVenda(TipoVenda.PESAVEL);assertTrue(new RetomadaVenda(v,Map.of(1,novo)).itens.isEmpty());
        novo.setTipoVenda(TipoVenda.UNIDADE);assertTrue(new RetomadaVenda(v,Map.of(1,novo)).itens.isEmpty());
    }
    @Test void semControleDeEstoqueRetomaQuantidadeMesmoZerado(){Product p=produto(1,"5",0);p.setControlaEstoque(false);assertEquals(7,new RetomadaVenda(venda(linha(p,7,"5")),Map.of(1,p)).itens.get(0).getQtd());}
    @Test void assinaturaDistingueMudancasDuranteAConfirmacao(){
        Product p=produto(1,"10",3);VendaSuspensa v=venda(linha(p,1,"10"));String h=new RetomadaVenda(v,Map.of(1,p)).assinatura;
        p.setEstoque(2);assertNotEquals(h,new RetomadaVenda(v,Map.of(1,p)).assinatura);p.setEstoque(3);p.setPermiteFiado(false);assertNotEquals(h,new RetomadaVenda(v,Map.of(1,p)).assinatura);
        p.setPermiteFiado(true);p.setNomeProduto("a:b");p.setCodBarras("c");String a=new RetomadaVenda(v,Map.of(1,p)).assinatura;p.setNomeProduto("a");p.setCodBarras("b:c");assertNotEquals(a,new RetomadaVenda(v,Map.of(1,p)).assinatura);
    }
    @Test void limitesEIdentificacaoSaoValidadosAntesDePersistir(){
        Product p=produto(1,"1",1);assertThrows(IllegalArgumentException.class,()->venda(linha(p,Integer.MAX_VALUE,"1")));
        p.setControlaEstoque(false);assertThrows(IllegalArgumentException.class,()->venda(linha(p,Integer.MAX_VALUE,"0"),linha(p,1,"0")));
        assertThrows(IllegalArgumentException.class,()->new Gravacao(UUID.randomUUID(),UUID.randomUUID(),0,null,"x".repeat(81),Estado.SUSPENSA,List.of(linha(p,1,"1"))));
        assertThrows(IllegalArgumentException.class,()->new Gravacao(UUID.randomUUID(),UUID.randomUUID(),0,null,"",Estado.SUSPENSA,List.of()));
    }
    @Test void snapshotNaoCompartilhaListaNemQuantidadesMutaveisComATela(){
        Product p=produto(1,"10",4);OrderItem i=new OrderItem(p,2,r("10"));List<OrderItem> itens=new ArrayList<>(List.of(i));List<Linha> copia=VendaSuspensa.capturar(itens);i.setQtd(4);itens.clear();
        assertEquals(2,copia.get(0).quantidade);assertThrows(UnsupportedOperationException.class,()->copia.clear());
    }
}
