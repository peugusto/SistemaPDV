package com.casarural.sistemapdv.model.entities;

import java.util.List;

/** Leitura do caixa encerrado e dos seus lançamentos, em uma mesma transação. */
public final class FechamentoCaixa {
    private final CashRegister caixa;
    private final CashSummary resumo;
    private final List<Order> vendas;
    private final List<CashMovement> movimentos;
    private final List<Payment> recebimentos;

    public FechamentoCaixa(CashRegister caixa, CashSummary resumo, List<Order> vendas,
                           List<CashMovement> movimentos, List<Payment> recebimentos) {
        this.caixa = caixa;
        this.resumo = resumo;
        this.vendas = List.copyOf(vendas);
        this.movimentos = List.copyOf(movimentos);
        this.recebimentos = List.copyOf(recebimentos);
    }
    public CashRegister getCaixa() { return caixa; }
    public CashSummary getResumo() { return resumo; }
    public List<Order> getVendas() { return vendas; }
    public List<CashMovement> getMovimentos() { return movimentos; }
    public List<Payment> getRecebimentos() { return recebimentos; }
}
