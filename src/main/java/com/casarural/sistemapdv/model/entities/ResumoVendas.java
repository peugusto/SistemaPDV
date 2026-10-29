package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/** Totais de vendas de um período (dashboard de vendas, protegido por PIN). */
public class ResumoVendas {

    private int quantidadeVendas;
    private BigDecimal faturamento = Money.ZERO;
    /** Parte das vendas do período que foi para o fiado (ainda não paga na hora). */
    private BigDecimal fiadoLancado = Money.ZERO;
    /** Pagamentos de fiado recebidos no período (de vendas de qualquer data). */
    private BigDecimal recebidoDeFiado = Money.ZERO;
    /** Quanto entrou na hora da venda por forma de pagamento; FIADO = fiadoLancado. */
    private final Map<PaymentMethod, BigDecimal> porForma = new EnumMap<>(PaymentMethod.class);
    /** Pago na hora com forma desconhecida (registros antigos, de antes de existir a forma de pagamento). */
    private BigDecimal outrasFormas = Money.ZERO;
    /** Soma dos descontos dados nas vendas do período. */
    private BigDecimal descontos = Money.ZERO;
    /** Vendas canceladas no período (sem contar as trocas). */
    private int canceladas;
    /** Soma dos itens vendidos (preço de venda, antes do desconto). */
    private BigDecimal receitaItens = Money.ZERO;
    /** Parte da receitaItens que tem custo informado (V14). */
    private BigDecimal receitaComCusto = Money.ZERO;
    /** Custo dos itens que têm custo informado. */
    private BigDecimal custoItens = Money.ZERO;
    /** Soma das parcelas já rateadas dentro de cada pedido; null quando não foi carregada. */
    private BigDecimal descontoComCusto;

    public int getQuantidadeVendas() { return quantidadeVendas; }
    public void setQuantidadeVendas(int quantidadeVendas) { this.quantidadeVendas = quantidadeVendas; }

    public BigDecimal getFaturamento() { return faturamento; }
    public void setFaturamento(BigDecimal faturamento) { this.faturamento = Money.of(faturamento); }

    public BigDecimal getFiadoLancado() { return fiadoLancado; }
    public void setFiadoLancado(BigDecimal fiadoLancado) { this.fiadoLancado = Money.of(fiadoLancado); }

    public BigDecimal getRecebidoDeFiado() { return recebidoDeFiado; }
    public void setRecebidoDeFiado(BigDecimal recebidoDeFiado) { this.recebidoDeFiado = Money.of(recebidoDeFiado); }

    public Map<PaymentMethod, BigDecimal> getPorForma() { return porForma; }

    public BigDecimal getPorForma(PaymentMethod forma) { return porForma.getOrDefault(forma, Money.ZERO); }

    public void setPorForma(PaymentMethod forma, BigDecimal valor) { porForma.put(forma, Money.of(valor)); }

    public BigDecimal getOutrasFormas() { return outrasFormas; }
    public void setOutrasFormas(BigDecimal outrasFormas) { this.outrasFormas = Money.of(outrasFormas); }

    public BigDecimal getDescontos() { return descontos; }
    public void setDescontos(BigDecimal descontos) { this.descontos = Money.of(descontos); }

    public int getCanceladas() { return canceladas; }
    public void setCanceladas(int canceladas) { this.canceladas = canceladas; }

    public BigDecimal getReceitaItens() { return receitaItens; }
    public void setReceitaItens(BigDecimal v) { this.receitaItens = Money.of(v); }
    public BigDecimal getReceitaComCusto() { return receitaComCusto; }
    public void setReceitaComCusto(BigDecimal v) { this.receitaComCusto = Money.of(v); }
    public BigDecimal getCustoItens() { return custoItens; }
    public void setCustoItens(BigDecimal v) { this.custoItens = Money.of(v); }

    /** Soma os custos de um pedido e só então agrega sua parcela de desconto ao período. */
    public void adicionarCustosDoPedido(BigDecimal receita, BigDecimal receitaComCusto,
                                       BigDecimal custo, BigDecimal descontoDoPedido) {
        receita = Money.of(receita);
        receitaComCusto = Money.of(receitaComCusto);
        BigDecimal parcela = receita.signum() == 0 ? Money.ZERO
                : Money.of(descontoDoPedido).multiply(receitaComCusto).divide(receita, Money.SCALE, Money.ROUNDING);
        // HALF_UP por pedido: a parte sem custo fica com descontoDoPedido - parcela,
        // preservando os centavos da venda. Nunca arredondar de novo sobre o total do período.
        this.receitaItens = this.receitaItens.add(receita);
        this.receitaComCusto = this.receitaComCusto.add(receitaComCusto);
        this.custoItens = this.custoItens.add(Money.of(custo));
        this.descontoComCusto = (this.descontoComCusto == null ? Money.ZERO : this.descontoComCusto).add(parcela);
    }

    /** Define uma parcela já apurada por pedido, sem inferi-la dos totais agregados. */
    public void setDescontoComCusto(BigDecimal valor) {
        this.descontoComCusto = valor == null ? null : Money.of(valor);
    }

    public boolean temReceitaComCusto() { return receitaComCusto.signum() > 0; }

    public boolean temCustoParcial() {
        return temReceitaComCusto() && receitaComCusto.compareTo(receitaItens) < 0;
    }

    /** Percentual apenas para apresentação. A existência/completude é decidida pelos valores reais. */
    public BigDecimal getCoberturaCusto() {
        if (receitaItens.signum() == 0) return null;
        return receitaComCusto.multiply(BigDecimal.valueOf(100)).divide(receitaItens, 1, RoundingMode.HALF_UP);
    }

    /**
     * Desconto explicitamente rateado por pedido. Totais do período não permitem reconstruí-lo.
     * Sem desconto algum a parcela é necessariamente zero; com desconto e sem apuração, null.
     */
    public BigDecimal getDescontoComCusto() {
        return descontoComCusto != null ? descontoComCusto : descontos.signum() == 0 ? Money.ZERO : null;
    }

    /** Lucro bruto: o que entrou nas vendas com custo, menos o custo delas e o desconto que lhes cabe. */
    public BigDecimal getLucroBruto() {
        BigDecimal parcela = getDescontoComCusto();
        return parcela == null ? null : receitaComCusto.subtract(parcela).subtract(custoItens);
    }

    /** Margem sobre o que foi recebido nessas vendas, em %. */
    public BigDecimal getMargem() {
        BigDecimal parcela = getDescontoComCusto();
        return parcela == null ? null : RegraLucro.margem(receitaComCusto.subtract(parcela), custoItens);
    }

    /** Valor médio de cada venda. */
    public BigDecimal getTicketMedio() {
        if (quantidadeVendas == 0) return Money.ZERO;
        return faturamento.divide(BigDecimal.valueOf(quantidadeVendas), Money.SCALE, RoundingMode.HALF_UP);
    }
}
