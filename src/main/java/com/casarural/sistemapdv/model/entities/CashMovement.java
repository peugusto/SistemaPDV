package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sangria ou reforço num caixa (tabela movimento_caixa). */
public class CashMovement {
    private int idMovimento;
    private int idCaixa;
    private CashMovementType tipo;
    private BigDecimal valor = Money.ZERO;
    private String motivo;
    private LocalDateTime dataMovimento;

    public int getIdMovimento() { return idMovimento; }
    public void setIdMovimento(int idMovimento) { this.idMovimento = idMovimento; }

    public int getIdCaixa() { return idCaixa; }
    public void setIdCaixa(int idCaixa) { this.idCaixa = idCaixa; }

    public CashMovementType getTipo() { return tipo; }
    public void setTipo(CashMovementType tipo) { this.tipo = tipo; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = Money.of(valor); }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public LocalDateTime getDataMovimento() { return dataMovimento; }
    public void setDataMovimento(LocalDateTime dataMovimento) { this.dataMovimento = dataMovimento; }
}
