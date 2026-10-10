package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.util.Objects;

public class Customer {
    private int idCliente;
    private String nomeCliente;
    private BigDecimal limiteCredito = Money.ZERO;
    private CustomerStatus situacaoFiado;
    private BigDecimal totalDevendo = Money.ZERO;

    public Customer(){}
    public Customer(String nomeCliente, BigDecimal limiteCredito, CustomerStatus situacaoFiado) {
        this.nomeCliente = nomeCliente;
        this.limiteCredito = Money.of(limiteCredito);
        this.situacaoFiado = situacaoFiado;
    }

    public int getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(int idCliente) {
        this.idCliente = idCliente;
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public void setNomeCliente(String nomeCliente) {
        this.nomeCliente = nomeCliente;
    }

    public BigDecimal getLimiteCredito() {
        return limiteCredito;
    }

    public void setLimiteCredito(BigDecimal limiteCredito) {
        this.limiteCredito = Money.of(limiteCredito);
    }

    public CustomerStatus getSituacaoFiado() {
        return situacaoFiado;
    }

    public void setSituacaoFiado(CustomerStatus situacaoFiado) {
        this.situacaoFiado = situacaoFiado;
    }

    public BigDecimal getTotalDevendo() {
        return totalDevendo;
    }
    public void setTotalDevendo(BigDecimal totalDevendo){
        this.totalDevendo = Money.of(totalDevendo);
    }
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return idCliente == customer.idCliente && Objects.equals(limiteCredito, customer.limiteCredito) && Objects.equals(nomeCliente, customer.nomeCliente) && situacaoFiado == customer.situacaoFiado;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCliente, nomeCliente, limiteCredito, situacaoFiado);
    }

    @Override
    public String toString() {
        return nomeCliente;
}}
