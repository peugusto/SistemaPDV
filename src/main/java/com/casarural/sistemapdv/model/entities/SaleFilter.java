package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import java.time.LocalDate;

/** Filtros do histórico de vendas. Campos nulos ou vazios = sem filtro. */
public class SaleFilter {
    private LocalDate inicio;
    private LocalDate fim;
    private PaymentMethod forma;
    private OrderStatus status;
    private String cliente;
    private String produto;

    public SaleFilter(LocalDate inicio, LocalDate fim) {
        this.inicio = inicio;
        this.fim = fim;
    }

    public LocalDate getInicio() { return inicio; }
    public LocalDate getFim() { return fim; }

    public PaymentMethod getForma() { return forma; }
    public SaleFilter setForma(PaymentMethod forma) { this.forma = forma; return this; }

    public OrderStatus getStatus() { return status; }
    public SaleFilter setStatus(OrderStatus status) { this.status = status; return this; }

    public String getCliente() { return cliente; }
    public SaleFilter setCliente(String cliente) { this.cliente = vazioParaNull(cliente); return this; }

    public String getProduto() { return produto; }
    public SaleFilter setProduto(String produto) { this.produto = vazioParaNull(produto); return this; }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
