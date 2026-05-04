package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {
    private int idPedido;
    private Customer costumer;
    private LocalDateTime dataPedido;
    private double valorTotal;
    private List<OrderItem> itemPedido;
    private OrderStatus status;
    private List<PaymentSlice> pagamentos = new ArrayList<>();

    public static class PaymentSlice {
        private Double valor;
        private PaymentMethod metodo;

        public PaymentSlice(Double valor, PaymentMethod metodo) {
            this.valor = valor;
            this.metodo = metodo;
        }

        public Double getValor() { return valor; }
        public PaymentMethod getMetodo() { return metodo; }
    }

    public Order(){}

    public Order(int idPedido, Customer costumer, LocalDateTime dataPedido, double valorTotal, List<OrderItem> itemPedido, OrderStatus status) {
        this.idPedido = idPedido;
        this.costumer = costumer;
        this.dataPedido = dataPedido;
        this.valorTotal = valorTotal;
        this.itemPedido = itemPedido;
        this.status = status;
    }

    public int getIdPedido() {
        return idPedido;
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

    public double getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(double valorTotal) {
        this.valorTotal = valorTotal;
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
        return idPedido == order.idPedido && Double.compare(valorTotal, order.valorTotal) == 0 && Objects.equals(costumer, order.costumer) && Objects.equals(dataPedido, order.dataPedido) && Objects.equals(itemPedido, order.itemPedido) && status == order.status;
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