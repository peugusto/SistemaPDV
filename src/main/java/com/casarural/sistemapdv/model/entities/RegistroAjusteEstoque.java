package com.casarural.sistemapdv.model.entities;

import java.time.LocalDateTime;

/** Retrato do ajuste confirmado, inclusive quando o estoque já mudou novamente. */
public final class RegistroAjusteEstoque {
    public final AjusteEstoque ajuste;
    public final String nome, codigo;
    public final int antes, depois;
    public final long reposicaoAntes, reposicaoDepois;
    public final LocalDateTime data;
    public RegistroAjusteEstoque(AjusteEstoque ajuste,String nome,String codigo,int antes,int depois,long reposicaoAntes,long reposicaoDepois,LocalDateTime data){
        this.ajuste=ajuste;this.nome=nome;this.codigo=codigo;this.antes=antes;this.depois=depois;this.reposicaoAntes=reposicaoAntes;this.reposicaoDepois=reposicaoDepois;this.data=data;
    }
}
