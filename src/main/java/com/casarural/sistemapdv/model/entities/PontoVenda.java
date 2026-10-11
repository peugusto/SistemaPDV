package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;

/** Um ponto dos gráficos de vendas: um dia, uma hora ou um dia da semana. */
public class PontoVenda {

    private final String rotulo;
    private int vendas;
    private BigDecimal valor = Money.ZERO;

    public PontoVenda(String rotulo) {
        this.rotulo = rotulo;
    }

    public PontoVenda(String rotulo, int vendas, BigDecimal valor) {
        this.rotulo = rotulo;
        this.vendas = vendas;
        this.valor = Money.of(valor);
    }

    public String getRotulo() { return rotulo; }
    public int getVendas() { return vendas; }
    public BigDecimal getValor() { return valor; }

    public void somar(int vendas, BigDecimal valor) {
        this.vendas += vendas;
        this.valor = this.valor.add(Money.of(valor));
    }
}
