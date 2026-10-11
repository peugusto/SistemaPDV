package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.util.Objects;

public class Product {
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

    public void adicionarEstoque(int quantidade) { this.estoque += quantidade;}

    public boolean isPermiteFiado() {
        return permiteFiado;
    }

    public void setPermiteFiado(boolean permiteFiado) {
        this.permiteFiado = permiteFiado;
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
        return !isControlaEstoque() || quantidade <= estoque;
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
