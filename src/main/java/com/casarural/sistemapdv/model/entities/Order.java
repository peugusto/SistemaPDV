package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {
    private int idPedido;
    private Customer costumer;
    private LocalDateTime dataPedido;
    private BigDecimal valorTotal = Money.ZERO;
    private List<OrderItem> itemPedido;
    private OrderStatus status;
    private List<PaymentSlice> pagamentos = new ArrayList<>();

    // Resumo de pagamentos de um pedido fiado (preenchido pela consulta da conta do cliente; não é gravado)
    private BigDecimal pagoNaVenda = Money.ZERO;
    private BigDecimal recebidoDepois = Money.ZERO;

    public static class PaymentSlice {
        private BigDecimal valor;
        private PaymentMethod metodo;

        public PaymentSlice(BigDecimal valor, PaymentMethod metodo) {
            this.valor = Money.of(valor);
            this.metodo = metodo;
        }

        public BigDecimal getValor() { return valor; }
        public PaymentMethod getMetodo() { return metodo; }
    }

    public Order(){}

    public Order(int idPedido, Customer costumer, LocalDateTime dataPedido, BigDecimal valorTotal, List<OrderItem> itemPedido, OrderStatus status) {
        this.idPedido = idPedido;
        this.costumer = costumer;
        this.dataPedido = dataPedido;
        this.valorTotal = Money.of(valorTotal);
        this.itemPedido = itemPedido;
        this.status = status;
    }

    public int getIdPedido() {
        return idPedido;
    }

    /** Parte paga na hora da compra (PIX, cartão, dinheiro), numa venda que teve parte no fiado. */
    public BigDecimal getPagoNaVenda() {
        return pagoNaVenda;
    }

    public void setPagoNaVenda(BigDecimal pagoNaVenda) {
        this.pagoNaVenda = Money.of(pagoNaVenda);
    }

    /** Parte paga depois, em recebimentos de fiado. */
    public BigDecimal getRecebidoDepois() {
        return recebidoDepois;
    }

    public void setRecebidoDepois(BigDecimal recebidoDepois) {
        this.recebidoDepois = Money.of(recebidoDepois);
    }

    /** Quanto ainda falta pagar deste pedido. */
    public BigDecimal getFalta() {
        return Money.max(Money.ZERO, valorTotal.subtract(pagoNaVenda).subtract(recebidoDepois));
    }

    public void setIdPedido(int idPedido) {
        this.idPedido = idPedido;
    }

    public Customer getCostumer() {
        return costumer;
    }

    public void setCostumer(Customer costumer) {
        this.costumer = costumer;
    }

    public LocalDateTime getDataPedido() {
        return dataPedido;
    }

    public void setDataPedido(LocalDateTime dataPedido) {
        this.dataPedido = dataPedido;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = Money.of(valorTotal);
    }

    public List<OrderItem> getItemPedido() {
        return itemPedido;
    }

    public void setItemPedido(List<OrderItem> itemPedido) {
        this.itemPedido = itemPedido;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<PaymentSlice> getPagamentos() {
        return pagamentos;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return idPedido == order.idPedido && Objects.equals(valorTotal, order.valorTotal) && Objects.equals(costumer, order.costumer) && Objects.equals(dataPedido, order.dataPedido) && Objects.equals(itemPedido, order.itemPedido) && status == order.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idPedido, costumer, dataPedido, valorTotal, itemPedido, status);
    }

    @Override
    public String toString() {
        return "Order{" +
                "idPedido=" + idPedido +
                ", costumer=" + costumer +
                ", dataPedido=" + dataPedido +
                ", valorTotal=" + valorTotal +
                ", itemPedido=" + itemPedido +
                ", status=" + status +
                '}';
    }
}