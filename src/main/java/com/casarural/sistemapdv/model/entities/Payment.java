package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Payment {
    private int idPagamento;
    private Order order;
    private BigDecimal valorPago = Money.ZERO;
    private LocalDateTime dataPagamento;
    private PaymentMethod metodo;
    private PaymentOrigin origem = PaymentOrigin.VENDA;
    /** Pedidos abatidos por este recebimento, ex.: "#32, #36" (um recebimento pode quitar vários). */
    private String pedidos;
    private Integer idCaixa;
    private boolean estornado;

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

    public PaymentOrigin getOrigem() {
        return origem;
    }

    public void setOrigem(PaymentOrigin origem) {
        this.origem = origem;
    }

    /** Caixa em que o pagamento entrou (só os do caixa aberto podem ser estornados). */
    public Integer getIdCaixa() {
        return idCaixa;
    }

    public void setIdCaixa(Integer idCaixa) {
        this.idCaixa = idCaixa;
    }

    /** true = desfeito (venda cancelada/trocada ou recebimento estornado); vem de pagamento_estornado. */
    public boolean isEstornado() {
        return estornado;
    }

    public void setEstornado(boolean estornado) {
        this.estornado = estornado;
    }

    public String getPedidos() {
        return pedidos;
    }

    public void setPedidos(String pedidos) {
        this.pedidos = pedidos;
    }
}
