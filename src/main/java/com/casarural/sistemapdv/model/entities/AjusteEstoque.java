package com.casarural.sistemapdv.model.entities;

import java.util.Objects;
import java.util.UUID;

/** Identidade e conteúdo imutáveis até a confirmação; nunca reaplica uma perda no retry. */
public final class AjusteEstoque {
    public enum Tipo {
        PERDA("Perda"), CONTAGEM("Correção de contagem");
        private final String nome;
        Tipo(String nome){this.nome=nome;}
        @Override public String toString(){return nome;}
    }
    public final UUID operacao;
    public final int produto, saldoEsperado, informado;
    public final Tipo tipo;
    public final String motivo;
    public AjusteEstoque(UUID operacao,int produto,Tipo tipo,int saldoEsperado,int informado,String motivo){
        this.operacao=Objects.requireNonNull(operacao,"Identificação do ajuste não informada.");
        this.tipo=Objects.requireNonNull(tipo,"Tipo de ajuste não informado.");
        if(produto<=0)throw new IllegalArgumentException("Selecione um produto.");
        if(saldoEsperado<0||informado<0)throw new IllegalArgumentException("Digite uma quantidade inteira entre 0 e 2.147.483.647.");
        if(tipo==Tipo.PERDA&&(informado==0||informado>saldoEsperado))throw new IllegalArgumentException("A perda deve ser maior que zero e não pode ultrapassar o estoque disponível.");
        if(tipo==Tipo.CONTAGEM&&informado==saldoEsperado)throw new IllegalArgumentException("A quantidade contada é igual ao estoque atual. Nenhum ajuste é necessário.");
        this.produto=produto;this.saldoEsperado=saldoEsperado;this.informado=informado;this.motivo=validarMotivo(motivo);
    }
    public int saldoDepois(){return tipo==Tipo.PERDA?saldoEsperado-informado:informado;}
    public static String validarMotivo(String motivo){
        String m=motivo==null?"":motivo.trim();
        if(m.isEmpty()||m.length()>255)throw new IllegalArgumentException("Informe o motivo do ajuste, com até 255 caracteres.");
        return m;
    }
    public boolean mesmosDados(AjusteEstoque outro){return produto==outro.produto&&tipo==outro.tipo&&saldoEsperado==outro.saldoEsperado&&informado==outro.informado&&motivo.equals(outro.motivo);}
    public long reposicaoDepois(long antes){
        try{if(antes<0)throw new ArithmeticException();return Math.max(0,Math.addExact(antes,(long)saldoEsperado-saldoDepois()));}
        catch(ArithmeticException e){throw new IllegalArgumentException("A pendência de reposição ultrapassa o limite permitido. Confira a lista de compras antes de ajustar.");}
    }
}
