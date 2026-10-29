package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.util.Objects;

public class Product {
    /** Tamanhos das colunas no banco (V1): passar disso dá erro ao gravar. */
    public static final int TAMANHO_MAX_NOME = 30;
    public static final int TAMANHO_MAX_CODIGO = 60;

    private int idProduto;
    private String codBarras;
    private String nomeProduto;
    private BigDecimal precoProduto = Money.ZERO;
    private int estoque;
    /** false = não pode ser vendido no fiado (ex.: cigarro, bebida alcoólica). Coluna produto.permite_fiado (V8). */
    private boolean permiteFiado = true;
    /** Coluna produto.tipo_venda (V11). */
    private TipoVenda tipoVenda = TipoVenda.UNIDADE;
    /** false = a venda não confere nem baixa estoque. Coluna produto.controla_estoque (V11). */
    private boolean controlaEstoque = true;
    /** Último custo pago (por unidade; no pesável, por kg). null = não informado. Coluna produto.preco_custo (V14). */
    private BigDecimal precoCusto;
    /** false = desativado: não aparece na venda, na busca nem na lista de compras (V15). */
    private boolean ativo = true;

    public Product(){}

    public Product(String codBarras, String nomeProduto, BigDecimal precoProduto, int estoque) {
        this.codBarras = codBarras;
        this.nomeProduto = nomeProduto;
        this.precoProduto = Money.of(precoProduto);
        this.estoque = estoque;
    }

    public int getIdProduto() {
        return idProduto;
    }

    public void setIdProduto(int idProduto) {
        this.idProduto = idProduto;
    }

    public String getCodBarras() {
        return codBarras;
    }

    public void setCodBarras(String codBarras) {
        this.codBarras = codBarras;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public BigDecimal getPrecoProduto() {
        return precoProduto;
    }

    public void setPrecoProduto(BigDecimal precoProduto) {
        this.precoProduto = Money.of(precoProduto);
    }

    public int getEstoque() {
        return estoque;
    }

    public void setEstoque(int estoque) {
        this.estoque = estoque;
    }

    public void adicionarEstoque(int quantidade) {
        if (quantidade < 0) throw new IllegalArgumentException("A entrada não pode ser negativa.");
        final int novo;
        try { novo = Math.addExact(estoque, quantidade); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("A quantidade ultrapassa o limite de estoque.", e); }
        if (novo < 0) throw new IllegalArgumentException("O estoque não pode ser negativo.");
        estoque = novo;
    }

    private Object[] estadoOriginal;
    private Object[] valoresAtuais() {
        return new Object[]{codBarras, nomeProduto, precoProduto, estoque, permiteFiado,
                tipoVenda.name(), controlaEstoque, precoCusto, ativo};
    }
    public void registrarEstadoOriginal() { estadoOriginal = valoresAtuais(); }
    public void copiarEstadoOriginal(Product origem) {
        estadoOriginal = origem.estadoOriginal == null ? origem.valoresAtuais() : origem.estadoOriginal.clone();
    }
    public Object[] getEstadoOriginal() {
        if (estadoOriginal == null) throw new IllegalStateException("Reabra a edição do produto para carregar seu estado atual.");
        return estadoOriginal.clone();
    }

    public boolean isPermiteFiado() {
        return permiteFiado;
    }

    public void setPermiteFiado(boolean permiteFiado) {
        this.permiteFiado = permiteFiado;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public BigDecimal getPrecoCusto() {
        return precoCusto;
    }

    public void setPrecoCusto(BigDecimal precoCusto) {
        this.precoCusto = precoCusto == null ? null : Money.of(precoCusto);
    }

    public TipoVenda getTipoVenda() {
        return tipoVenda;
    }

    public void setTipoVenda(TipoVenda tipoVenda) {
        this.tipoVenda = tipoVenda == null ? TipoVenda.UNIDADE : tipoVenda;
    }

    public boolean isPesavel() {
        return tipoVenda == TipoVenda.PESAVEL;
    }

    /** Pesável nunca controla estoque: o valor digitado não diz quanto saiu da peça. */
    public boolean isControlaEstoque() {
        return controlaEstoque && !isPesavel();
    }

    public void setControlaEstoque(boolean controlaEstoque) {
        this.controlaEstoque = controlaEstoque;
    }

    /** Se dá para vender {@code quantidade} (somando o que já está no carrinho). Sem controle, sempre dá. */
    public boolean temEstoquePara(int quantidade) {
        return quantidade >= 0 && (!isControlaEstoque() || quantidade <= estoque);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return idProduto == product.idProduto && Objects.equals(precoProduto, product.precoProduto) && estoque == product.estoque && Objects.equals(codBarras, product.codBarras) && Objects.equals(nomeProduto, product.nomeProduto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProduto, codBarras, nomeProduto, precoProduto, estoque);
    }

    @Override
    public String toString() {
        return "Product{" +
                "idProduto=" + idProduto +
                ", codBarras='" + codBarras + '\'' +
                ", nomeProduto='" + nomeProduto + '\'' +
                ", preco=" + precoProduto +
                ", estoque=" + estoque +
                '}';
    }
}
