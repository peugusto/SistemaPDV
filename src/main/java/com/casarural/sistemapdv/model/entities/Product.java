package com.casarural.sistemapdv.model.entities;

import java.util.Objects;

public class Product {
    private int idProduto;
    private String codBarras;
    private String nomeProduto;
    private double precoProduto;
    private int estoque;

    public Product(){}

    public Product(String codBarras, String nomeProduto, double precoProduto, int estoque) {
        this.codBarras = codBarras;
        this.nomeProduto = nomeProduto;
        this.precoProduto = precoProduto;
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

    public double getPrecoProduto() {
        return precoProduto;
    }

    public void setPrecoProduto(double precoProduto) {
        this.precoProduto = precoProduto;
    }

    public int getEstoque() {
        return estoque;
    }

    public void setEstoque(int estoque) {
        this.estoque += estoque;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return idProduto == product.idProduto && Double.compare(precoProduto, product.precoProduto) == 0 && estoque == product.estoque && Objects.equals(codBarras, product.codBarras) && Objects.equals(nomeProduto, product.nomeProduto);
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
