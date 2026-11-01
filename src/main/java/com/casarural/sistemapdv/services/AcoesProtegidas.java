package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Product;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Compara valores efetivamente alterados; formatação monetária não exige autorização. */
public final class AcoesProtegidas {
    private AcoesProtegidas() {}
    private static boolean mudou(BigDecimal a,BigDecimal b) {
        return a==null?b!=null:b==null || a.compareTo(b)!=0;
    }
    public static List<String> produto(Product antes,Product depois) {
        if(antes==null || antes.getIdProduto()==0)return List.of("cadastrar produto com preço e estoque iniciais");
        List<String> a=new ArrayList<>();
        String codigoAntes=antes.getCodBarras()==null?"":antes.getCodBarras().trim();
        String codigoDepois=depois.getCodBarras()==null?"":depois.getCodBarras().trim();
        if(!codigoAntes.equals(codigoDepois))a.add("alterar código de barras");
        if(mudou(antes.getPrecoProduto(),depois.getPrecoProduto()))a.add("alterar preço de venda");
        if(mudou(antes.getPrecoCusto(),depois.getPrecoCusto()))a.add("alterar custo");
        if(antes.getEstoque()!=depois.getEstoque())a.add("ajustar estoque diretamente");
        if(antes.isPermiteFiado()!=depois.isPermiteFiado())a.add("alterar permissão de fiado");
        if(antes.isAtivo()!=depois.isAtivo())a.add(depois.isAtivo()?"reativar produto":"desativar produto");
        if(antes.getTipoVenda()!=depois.getTipoVenda())a.add("alterar tipo de venda");
        if(antes.isControlaEstoque()!=depois.isControlaEstoque())a.add("alterar controle de estoque");
        return List.copyOf(a);
    }
    public static List<String> cliente(Customer antes,Customer depois) {
        if(antes==null || antes.getIdCliente()==0) {
            return depois.getLimiteCredito().signum()>0?List.of("conceder limite inicial de fiado"):List.of();
        }
        List<String> a=new ArrayList<>();
        if(mudou(antes.getLimiteCredito(),depois.getLimiteCredito()))a.add("alterar limite de crédito");
        if(antes.getSituacaoFiado()==CustomerStatus.BLOQUEADO && depois.getSituacaoFiado()==CustomerStatus.DISPONIVEL)
            a.add("desbloquear cliente para fiado");
        return List.copyOf(a);
    }
}
