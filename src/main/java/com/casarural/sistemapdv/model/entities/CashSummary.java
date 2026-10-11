package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.model.entities.enums.PaymentOrigin;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Resumo de um caixa: só contas, sem banco e sem tela (por isso tem testes automatizados).
 *
 * Recebe os totais lançados (pagamentos por origem e forma, sangrias e reforços) e calcula:
 *   dinheiro na gaveta = troco inicial + vendas em dinheiro + fiado recebido em dinheiro + reforços - sangrias
 *   PIX e cartão       = o que entrou nessas formas (vendas + fiado recebido)
 *   vendido no fiado   = vendas que ficaram para pagar depois (não entra dinheiro)
 */
public class CashSummary {

    private final BigDecimal valorInicial;
    private final Map<PaymentMethod, BigDecimal> vendas = new EnumMap<>(PaymentMethod.class);
    private final Map<PaymentMethod, BigDecimal> recebimentos = new EnumMap<>(PaymentMethod.class);
    private BigDecimal sangrias = Money.ZERO;
    private BigDecimal reforcos = Money.ZERO;

    public CashSummary(BigDecimal valorInicial) {
        this.valorInicial = Money.of(valorInicial);
    }

    /** Soma um total de pagamentos (ex.: vendas em PIX). Pode ser chamado mais de uma vez para a mesma forma. */
    public CashSummary addPagamento(PaymentOrigin origem, PaymentMethod metodo, BigDecimal valor) {
        Map<PaymentMethod, BigDecimal> destino = origem == PaymentOrigin.RECEBIMENTO_FIADO ? recebimentos : vendas;
        destino.merge(metodo, Money.of(valor), BigDecimal::add);
        return this;
    }

    public CashSummary addMovimento(CashMovementType tipo, BigDecimal valor) {
        if (tipo == CashMovementType.SANGRIA) {
            sangrias = sangrias.add(Money.of(valor));
        } else {
            reforcos = reforcos.add(Money.of(valor));
        }
        return this;
    }

    public BigDecimal getValorInicial() { return valorInicial; }
    public BigDecimal getSangrias() { return sangrias; }
    public BigDecimal getReforcos() { return reforcos; }

    public BigDecimal getVendas(PaymentMethod metodo) {
        return vendas.getOrDefault(metodo, Money.ZERO);
    }

    public BigDecimal getRecebimentosFiado(PaymentMethod metodo) {
        return recebimentos.getOrDefault(metodo, Money.ZERO);
    }

    /** Dinheiro que deveria estar na gaveta agora. */
    public BigDecimal getDinheiroEsperado() {
        return valorInicial
                .add(getVendas(PaymentMethod.DINHEIRO))
                .add(getRecebimentosFiado(PaymentMethod.DINHEIRO))
                .add(reforcos)
                .subtract(sangrias);
    }

    /** Tudo que entrou em PIX (vendas + fiado recebido). */
    public BigDecimal getTotalPix() {
        return getVendas(PaymentMethod.PIX).add(getRecebimentosFiado(PaymentMethod.PIX));
    }

    /** Tudo que entrou em cartão (vendas + fiado recebido). */
    public BigDecimal getTotalCartao() {
        return getVendas(PaymentMethod.CARTAO).add(getRecebimentosFiado(PaymentMethod.CARTAO));
    }

    /** Vendas que ficaram para o cliente pagar depois. */
    public BigDecimal getVendidoNoFiado() {
        return getVendas(PaymentMethod.FIADO);
    }

    /** Total vendido no caixa, em qualquer forma (inclui o fiado). */
    public BigDecimal getTotalVendido() {
        return vendas.values().stream().reduce(Money.ZERO, BigDecimal::add);
    }

    /** Total de fiado recebido, em qualquer forma. */
    public BigDecimal getTotalFiadoRecebido() {
        return recebimentos.values().stream().reduce(Money.ZERO, BigDecimal::add);
    }

    /** Contado - esperado. Negativo = faltou dinheiro na gaveta. */
    public BigDecimal diferenca(BigDecimal contado) {
        return Money.of(contado).subtract(getDinheiroEsperado());
    }
}
