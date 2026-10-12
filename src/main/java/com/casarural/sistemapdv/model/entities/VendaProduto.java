package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Quanto um produto vendeu num período (dashboard de produtos). */
public class VendaProduto {

    private final Product produto;
    /** Unidades vendidas; no pesável, número de pesagens. */
    private final int quantidade;
    /** Em quantas vendas diferentes o produto apareceu. */
    private final int vendas;
    private final BigDecimal valor;
    /** Última venda do produto no período; null se não vendeu. */
    private final LocalDateTime ultimaVenda;
    /** Custo do que foi vendido (só das vendas com custo gravado); null = nenhuma venda com custo. */
    private BigDecimal custo;
    /** Valor das vendas que têm custo (para o lucro não misturar vendas sem custo). */
    private BigDecimal valorComCusto = Money.ZERO;

    public VendaProduto(Product produto, int quantidade, int vendas, BigDecimal valor, LocalDateTime ultimaVenda) {
        this.produto = produto;
        this.quantidade = quantidade;
        this.vendas = vendas;
        this.valor = Money.of(valor);
        this.ultimaVenda = ultimaVenda;
    }

    public Product getProduto() { return produto; }
    public int getQuantidade() { return quantidade; }
    public int getVendas() { return vendas; }
    public BigDecimal getValor() { return valor; }
    public LocalDateTime getUltimaVenda() { return ultimaVenda; }

    public boolean vendeu() { return quantidade > 0; }

    public BigDecimal getCusto() { return custo; }
    public BigDecimal getValorComCusto() { return valorComCusto; }

    public void setCusto(BigDecimal custo, BigDecimal valorComCusto) {
        this.custo = custo == null ? null : Money.of(custo);
        this.valorComCusto = Money.of(valorComCusto);
    }

    /** Lucro das vendas com custo (antes de descontos da venda); null sem custo. */
    public BigDecimal getLucro() {
        return custo == null ? null : RegraLucro.lucro(valorComCusto, custo);
    }

    public BigDecimal getMargem() {
        return custo == null ? null : RegraLucro.margem(valorComCusto, custo);
    }
}
