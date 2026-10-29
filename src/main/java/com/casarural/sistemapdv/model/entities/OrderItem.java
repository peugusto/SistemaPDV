package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderItem {
    private Product produto;
    private Order pedido;
    private int qtd;
    private Integer estoqueBaixado;
    public Integer getEstoqueBaixado() { return estoqueBaixado; }
    public void setEstoqueBaixado(Integer qtd) { if (qtd != null && qtd < 0) throw new IllegalArgumentException("Baixa inválida."); estoqueBaixado = qtd; }
    private BigDecimal precoUnitario = Money.ZERO;
    /** Custo de cada unidade no momento da venda (V14); null = produto sem custo. */
    private BigDecimal custoUnitario;

    public OrderItem(){}
    public OrderItem(Product produto, int qtd, BigDecimal precoUnitario) {
        this.produto = produto;
        if (qtd <= 0) throw new IllegalArgumentException("A quantidade do item deve ser maior que zero.");
        this.qtd = qtd;
        this.precoUnitario = Money.of(precoUnitario);
        // custo do momento da venda (no pesável, proporcional ao valor da balança)
        this.custoUnitario = produto == null ? null : RegraLucro.custoDoItem(produto, this.precoUnitario);
    }

    public Order getPedido() {
        return pedido;
    }

    public Integer getIdPedido() {
        return (pedido != null) ? pedido.getIdPedido() : null;
    }

    public String getcodBarras() {
        return (produto != null) ? produto.getCodBarras() : null;
    }

    public LocalDateTime getDataPedido(){
        return (pedido != null) ? pedido.getDataPedido() : null;
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
        if (qtd <= 0) throw new IllegalArgumentException("A quantidade do item deve ser maior que zero.");
        this.qtd = qtd;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = Money.of(precoUnitario);
    }

    // Calculado sempre a partir de preço x quantidade, nunca armazenado
    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    public void setCustoUnitario(BigDecimal custoUnitario) {
        this.custoUnitario = custoUnitario == null ? null : Money.of(custoUnitario);
    }

    public BigDecimal getSubtotal() {
        if (qtd <= 0 || precoUnitario.signum() < 0) throw new IllegalStateException("Quantidade ou preço inválido no item.");
        return Money.of(precoUnitario.multiply(BigDecimal.valueOf(qtd)));
    }


    public String getNomeProduto() {
        return produto != null ? produto.getNomeProduto() : "";
    }

    public String getCodBarras() {
        return produto != null ? produto.getCodBarras() : "";
    }
}
