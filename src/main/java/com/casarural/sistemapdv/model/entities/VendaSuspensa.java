package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Snapshot imutável do carrinho. Não contém pagamentos ou descontos autorizados. */
public final class VendaSuspensa {
    public enum Estado { SUSPENSA, EM_USO, EM_PAGAMENTO, CONCLUIDA, DESCARTADA }
    public static final BigDecimal MAXIMO = new BigDecimal("99999999.99");
    public static final class Linha {
        public final int produto, quantidade;
        public final String nome, codigo;
        public final BigDecimal preco, precoCadastro;
        public final TipoVenda tipo;
        public Linha(int produto,String nome,String codigo,int quantidade,BigDecimal preco,TipoVenda tipo,BigDecimal precoCadastro) {
            this.produto=produto;this.nome=Objects.requireNonNull(nome);this.codigo=Objects.requireNonNull(codigo);
            this.quantidade=quantidade;this.preco=Money.of(preco);this.tipo=Objects.requireNonNull(tipo);this.precoCadastro=Money.of(precoCadastro);
            if(produto<=0 || nome.isBlank() || nome.length()>30 || codigo.length()>60 || quantidade<=0
                    || this.preco.signum()<0 || this.preco.compareTo(MAXIMO)>0 || this.precoCadastro.signum()<0 || this.precoCadastro.compareTo(MAXIMO)>0
                    || tipo==TipoVenda.PESAVEL && quantidade!=1)throw new IllegalArgumentException("Revise os itens antes de suspender a venda.");
        }
        public static Linha de(OrderItem item) {
            Product p=Objects.requireNonNull(item.getProduto());
            return new Linha(p.getIdProduto(),p.getNomeProduto(),p.getCodBarras(),item.getQtd(),item.getPrecoUnitario(),p.getTipoVenda(),p.getPrecoProduto());
        }
        public BigDecimal total(){return Money.of(preco.multiply(BigDecimal.valueOf(quantidade)));}
        @Override public boolean equals(Object o){if(!(o instanceof Linha))return false;Linha l=(Linha)o;return produto==l.produto&&quantidade==l.quantidade&&nome.equals(l.nome)&&codigo.equals(l.codigo)&&preco.equals(l.preco)&&precoCadastro.equals(l.precoCadastro)&&tipo==l.tipo;}
        @Override public int hashCode(){return Objects.hash(produto,quantidade,nome,codigo,preco,precoCadastro,tipo);}
    }
    public final UUID id;
    public final String identificacao, sessao, token;
    public final long versao;
    public final Estado estado;
    public final LocalDateTime criadaEm, atualizadaEm;
    public final Integer pedido;
    public final List<Linha> itens;
    public VendaSuspensa(UUID id,String identificacao,long versao,Estado estado,String sessao,String token,
                         LocalDateTime criadaEm,LocalDateTime atualizadaEm,Integer pedido,List<Linha> itens) {
        this.id=id;this.identificacao=identificacao;this.versao=versao;this.estado=estado;this.sessao=sessao;this.token=token;
        this.criadaEm=criadaEm;this.atualizadaEm=atualizadaEm;this.pedido=pedido;this.itens=List.copyOf(itens);validarItens(itens,true);
    }
    public String getIdentificacao(){return identificacao.isBlank()?"Venda sem identificação":identificacao;}
    public LocalDateTime getAtualizadaEm(){return atualizadaEm;}
    public int getItens(){return itens.size();}
    public BigDecimal getTotal(){return total(itens);}
    public String getSituacao(){return estado==Estado.EM_PAGAMENTO?"Pagamento interrompido — conferir":estado==Estado.EM_USO?"Carrinho recuperável":"Suspensa";}
    public static List<Linha> capturar(List<OrderItem> itens){List<Linha> r=new ArrayList<>();for(OrderItem i:itens)r.add(Linha.de(i));validarItens(r,true);return List.copyOf(r);}
    public static BigDecimal total(List<Linha> itens){return itens.stream().map(Linha::total).reduce(Money.ZERO,BigDecimal::add);}
    public static void validarItens(List<Linha> itens,boolean vazioPermitido){
        if(itens==null || itens.size()>500 || !vazioPermitido&&itens.isEmpty() || total(itens).compareTo(MAXIMO)>0)
            throw new IllegalArgumentException("A venda suspensa deve ter até 500 linhas e total de até R$ 99.999.999,99.");
        Map<Integer,Long> quantidades=new HashMap<>();
        for(Linha l:itens)if(quantidades.merge(l.produto,(long)l.quantidade,Long::sum)>Integer.MAX_VALUE)
            throw new IllegalArgumentException("A quantidade total de um produto ultrapassa o limite permitido.");
    }
    /** Uma operação conserva a mesma identidade e conteúdo até ter resposta confirmada. */
    public static final class Gravacao {
        public final UUID id, operacao;
        public final long versao;
        public final String token, identificacao;
        public final Estado destino;
        public final boolean liberar;
        public final List<Linha> itens;
        public Gravacao(UUID id,UUID operacao,long versao,String token,String identificacao,Estado destino,List<Linha> itens) {
            this(id,operacao,versao,token,identificacao,destino,itens,false);
        }
        public Gravacao(UUID id,UUID operacao,long versao,String token,String identificacao,Estado destino,List<Linha> itens,boolean liberar) {
            this.liberar=liberar;
            this.id=Objects.requireNonNull(id);this.operacao=Objects.requireNonNull(operacao);this.versao=versao;this.token=token;
            this.identificacao=identificacao==null?"":identificacao.trim();this.destino=Objects.requireNonNull(destino);this.itens=List.copyOf(itens);
            if(this.identificacao.length()>80 || versao<0 || destino==Estado.CONCLUIDA)throw new IllegalArgumentException("Identificação inválida (máximo 80 caracteres).");
            validarItens(itens,destino!=Estado.SUSPENSA);
        }
    }
}
