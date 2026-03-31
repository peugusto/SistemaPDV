package com.casarural.sistemapdv.model.entities;

public class OrderItem {
    private Product produto;
    private Order pedido;
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

    public Order getPedido() {
        return pedido;
    }

    public Integer getIdPedido() {
        return (pedido != null) ? pedido.getIdPedido() : null;
    }

    public void setPedido(Order pedido) {
        this.pedido = pedido;
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

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public double getSubtotal() {
        return qtd * precoUnitario;
    }


    public String getNomeProduto() {
        return produto != null ? produto.getNomeProduto() : "";
    }

    public String getCodBarras() {
        return produto != null ? produto.getCodBarras() : "";
    }
}
