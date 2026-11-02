package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.VendaSuspensa.Linha;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Revalida o cadastro sem cobrar de novo valores pesados numa tabela de preço diferente. */
public final class RetomadaVenda {
    public final VendaSuspensa original;
    public final Map<Integer,Product> produtos;
    public final List<OrderItem> itens;
    public final List<String> avisos;
    public final String assinatura;
    public RetomadaVenda(VendaSuspensa original,Map<Integer,Product> produtos) {
        this.original=original;this.produtos=Map.copyOf(produtos);
        List<OrderItem> novos=new ArrayList<>();List<String> mensagens=new ArrayList<>();Map<Integer,Integer> usados=new HashMap<>();
        for(Linha l:original.itens) {
            Product p=produtos.get(l.produto);
            if(p==null || !p.isAtivo()){mensagens.add(l.nome+": será retirado (produto "+(p==null?"excluído":"desativado")+").");continue;}
            if(p.getTipoVenda()!=l.tipo || l.tipo==TipoVenda.PESAVEL && p.getPrecoProduto().compareTo(l.precoCadastro)!=0){mensagens.add(l.nome+": será retirado; o tipo/preço por peso mudou. Lance ou pese novamente.");continue;}
            int qtd=l.quantidade;
            if(p.isControlaEstoque())qtd=Math.min(qtd,Math.max(0,p.getEstoque()-usados.getOrDefault(l.produto,0)));
            if(qtd!=l.quantidade)mensagens.add(l.nome+": quantidade de "+l.quantidade+" para "+qtd+" (estoque atual).");
            if(qtd==0)continue;
            usados.merge(l.produto,qtd,Math::addExact);
            BigDecimal preco=l.tipo==TipoVenda.PESAVEL?l.preco:p.getPrecoProduto();
            if(preco.compareTo(l.preco)!=0)mensagens.add(l.nome+": preço de "+Money.format(l.preco)+" para "+Money.format(preco)+".");
            if(!p.getNomeProduto().equals(l.nome)||!p.getCodBarras().equals(l.codigo))mensagens.add(l.nome+": cadastro atualizado para "+p.getNomeProduto()+".");
            novos.add(new OrderItem(p,qtd,preco));
        }
        this.itens=List.copyOf(novos);this.avisos=List.copyOf(mensagens);VendaSuspensa.validarItens(VendaSuspensa.capturar(novos),true);
        assinatura=assinatura(original,produtos);
    }
    private static String assinatura(VendaSuspensa v,Map<Integer,Product> produtos) {
        try {
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            for(Linha l:v.itens) {
                Product p=produtos.get(l.produto);
                Object[] campos=p==null?new Object[]{l.produto,"ausente"}:new Object[]{l.produto,p.getNomeProduto(),p.getCodBarras(),p.getPrecoProduto(),p.getEstoque(),p.isAtivo(),p.getTipoVenda(),p.isControlaEstoque(),p.isPermiteFiado(),p.getPrecoCusto()};
                for(Object campo:campos){byte[] b=String.valueOf(campo).getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}
            }
            return Base64.getEncoder().encodeToString(d.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
