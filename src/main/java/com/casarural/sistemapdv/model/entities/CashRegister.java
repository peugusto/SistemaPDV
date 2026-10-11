package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Um caixa: do troco inicial, na abertura, até o fechamento (tabela caixa). */
public class CashRegister {
    private int idCaixa;
    private LocalDateTime abertoEm;
    private BigDecimal valorInicial = Money.ZERO;
    private LocalDateTime fechadoEm;
    private BigDecimal valorEsperado;
    /** Dinheiro contado na gaveta; null = fechado sem conferência. */
    private BigDecimal valorContado;
    private String observacao;

    public boolean isAberto() {
        return fechadoEm == null;
    }

    /** Contado - esperado; null quando não houve conferência. Negativo = faltou dinheiro. */
    public BigDecimal getDiferenca() {
        if (valorContado == null || valorEsperado == null) return null;
        return valorContado.subtract(valorEsperado);
    }

    public int getIdCaixa() { return idCaixa; }
    public void setIdCaixa(int idCaixa) { this.idCaixa = idCaixa; }

    public LocalDateTime getAbertoEm() { return abertoEm; }
    public void setAbertoEm(LocalDateTime abertoEm) { this.abertoEm = abertoEm; }

    public BigDecimal getValorInicial() { return valorInicial; }
    public void setValorInicial(BigDecimal valorInicial) { this.valorInicial = Money.of(valorInicial); }

    public LocalDateTime getFechadoEm() { return fechadoEm; }
    public void setFechadoEm(LocalDateTime fechadoEm) { this.fechadoEm = fechadoEm; }

    public BigDecimal getValorEsperado() { return valorEsperado; }
    public void setValorEsperado(BigDecimal valorEsperado) { this.valorEsperado = valorEsperado == null ? null : Money.of(valorEsperado); }

    public BigDecimal getValorContado() { return valorContado; }
    public void setValorContado(BigDecimal valorContado) { this.valorContado = valorContado == null ? null : Money.of(valorContado); }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
