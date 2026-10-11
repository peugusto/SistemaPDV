package com.casarural.sistemapdv.model.entities;

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
