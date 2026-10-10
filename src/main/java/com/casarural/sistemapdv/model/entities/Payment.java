package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Payment {
    private int idPagamento;
    private Order order;
    private BigDecimal valorPago = Money.ZERO;
    private LocalDateTime dataPagamento;
    private PaymentMethod metodo;

    public Payment(){}
    public Payment(Order order, BigDecimal valorPago, LocalDateTime dataPagamento, PaymentMethod metodo) {
        this.order = order;
        this.valorPago = Money.of(valorPago);
        this.dataPagamento = dataPagamento;
        this.metodo = metodo;
    }

    public int getIdPagamento() {
        return idPagamento;
    }

    public void setIdPagamento(int idPagamento) {
        this.idPagamento = idPagamento;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public void setValorPago(BigDecimal valorPago) {
        this.valorPago = Money.of(valorPago);
    }

    public LocalDateTime getDataPagamento() {
        return dataPagamento;
    }

    public void setDataPagamento(LocalDateTime dataPagamento) {
        this.dataPagamento = dataPagamento;
    }

    public PaymentMethod getMetodo() {
        return metodo;
    }

    public void setMetodo(PaymentMethod metodo) {
        this.metodo = metodo;
    }
}
