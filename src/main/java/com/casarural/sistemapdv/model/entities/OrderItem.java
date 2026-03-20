package com.casarural.sistemapdv.model.entities;

public class OrderItem {
    private Product produto;
    private int qtd;
    private double precoUnitario;
    private double subtotal;

    public OrderItem(){}
    public OrderItem(Product produto, int qtd, double precoUnitario, double subtotal) {
        this.produto = produto;
        this.qtd = qtd;
        this.precoUnitario = precoUnitario;
        this.subtotal = subtotal;
    }

    public Product getProduto() {
        return produto;
    }

    public void setProduto(Product produto) {
        this.produto = produto;
    }

    public int getQtd() {
        return qtd;
    }

    public void setQtd(int qtd) {
        this.qtd = qtd;
    }

    public double getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(double precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }
}
