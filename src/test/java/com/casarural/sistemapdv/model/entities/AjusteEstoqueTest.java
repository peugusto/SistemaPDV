package com.casarural.sistemapdv.model.entities;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AjusteEstoqueTest {
    static AjusteEstoque ajuste(AjusteEstoque.Tipo tipo,int saldo,int informado,String motivo){return new AjusteEstoque(UUID.randomUUID(),7,tipo,saldo,informado,motivo);}
    @Test void perdaDescontaUnidadesEContagemDefineSaldoFinal(){
        AjusteEstoque perda=ajuste(AjusteEstoque.Tipo.PERDA,20,2,"Embalagens danificadas");assertEquals(18,perda.saldoDepois());assertEquals(7,perda.reposicaoDepois(5));
        AjusteEstoque contagem=ajuste(AjusteEstoque.Tipo.CONTAGEM,18,17,"Contagem física");assertEquals(17,contagem.saldoDepois());assertEquals(8,contagem.reposicaoDepois(7));
    }
    @Test void contagemPodeZerarOuAumentarSemReposicaoNegativa(){
        assertEquals(0,ajuste(AjusteEstoque.Tipo.CONTAGEM,20,0,"Contagem").saldoDepois());
        assertEquals(0,ajuste(AjusteEstoque.Tipo.CONTAGEM,20,24,"Contagem").reposicaoDepois(2));
        assertEquals(2147483647,ajuste(AjusteEstoque.Tipo.CONTAGEM,0,Integer.MAX_VALUE,"Contagem").saldoDepois());
    }
    @Test void perdaInvalidaOuContagemSemMudancaNaoGeraOperacao(){
        for(int q:new int[]{0,-1,21})assertThrows(IllegalArgumentException.class,()->ajuste(AjusteEstoque.Tipo.PERDA,20,q,"Perda"));
        assertThrows(IllegalArgumentException.class,()->ajuste(AjusteEstoque.Tipo.CONTAGEM,20,20,"Contagem"));
        assertThrows(IllegalArgumentException.class,()->ajuste(AjusteEstoque.Tipo.CONTAGEM,20,-1,"Contagem"));
    }
    @Test void motivoObrigatorioPreservadoSemTruncar(){
        for(String m:new String[]{null,"","  ","a".repeat(256)})assertThrows(IllegalArgumentException.class,()->ajuste(AjusteEstoque.Tipo.PERDA,20,1,m));
        assertEquals("Vencimento",ajuste(AjusteEstoque.Tipo.PERDA,20,1," Vencimento ").motivo);
        assertEquals(255,ajuste(AjusteEstoque.Tipo.PERDA,20,1,"a".repeat(255)).motivo.length());
    }
    @Test void mesmoConteudoPodeSerConfirmadoMasDadosAlteradosNao(){
        AjusteEstoque a=ajuste(AjusteEstoque.Tipo.PERDA,20,2,"Quebra");assertTrue(a.mesmosDados(new AjusteEstoque(a.operacao,7,a.tipo,20,2,"Quebra")));
        assertFalse(a.mesmosDados(new AjusteEstoque(a.operacao,7,a.tipo,20,3,"Quebra")));
        assertFalse(a.mesmosDados(new AjusteEstoque(a.operacao,7,a.tipo,20,2,"Vencimento")));
        assertFalse(a.mesmosDados(new AjusteEstoque(a.operacao,8,a.tipo,20,2,"Quebra")));
    }
    @Test void limiteDaReposicaoNaoSaturaNemEstoura(){
        AjusteEstoque a=ajuste(AjusteEstoque.Tipo.PERDA,2,1,"Quebra");assertEquals(Long.MAX_VALUE,a.reposicaoDepois(Long.MAX_VALUE-1));
        assertEquals((long)Integer.MAX_VALUE+1,a.reposicaoDepois(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->a.reposicaoDepois(Long.MAX_VALUE));
    }
}
