package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;

import java.util.Objects;

public class Customer {
    private int idCliente;
    private String nomeCliente;
    private double limiteCredito;
    private CustomerStatus situacaoFiado;
    private Double totalDevendo;

    public Customer(){}
    public Customer(String nomeCliente, double limiteCredito, CustomerStatus situacaoFiado) {
        this.nomeCliente = nomeCliente;
        this.limiteCredito = limiteCredito;
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

    public double getLimiteCredito() {
        return limiteCredito;
    }

    public void setLimiteCredito(double limiteCredito) {
        this.limiteCredito = limiteCredito;
    }

    public CustomerStatus getSituacaoFiado() {
        return situacaoFiado;
    }

    public void setSituacaoFiado(CustomerStatus situacaoFiado) {
        this.situacaoFiado = situacaoFiado;
    }

    public Double getTotalDevendo() {
        return totalDevendo;
    }
    public void setTotalDevendo(Double totalDevendo){
        this.totalDevendo = totalDevendo;
    }
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return idCliente == customer.idCliente && Double.compare(limiteCredito, customer.limiteCredito) == 0 && Objects.equals(nomeCliente, customer.nomeCliente) && situacaoFiado == customer.situacaoFiado;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCliente, nomeCliente, limiteCredito, situacaoFiado);
    }

    @Override
    public String toString() {
        return nomeCliente;
}}
