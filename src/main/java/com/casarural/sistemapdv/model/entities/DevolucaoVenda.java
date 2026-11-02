package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Operação de uma venda de outro caixa, com pagamentos anteriores e restituição efetiva separados. */
public final class DevolucaoVenda {
    public final int pedido;
    public final String tipo, motivo;
    public final LocalDateTime data;
    public final BigDecimal fiadoBaixado;
    public final Map<PaymentMethod,BigDecimal> compensado, devolvido;
    public DevolucaoVenda(int pedido,String tipo,String motivo,LocalDateTime data,BigDecimal fiadoBaixado,
                          Map<PaymentMethod,BigDecimal> compensado,Map<PaymentMethod,BigDecimal> devolvido){
        this.pedido=pedido;this.tipo=tipo;this.motivo=motivo;this.data=data;this.fiadoBaixado=fiadoBaixado;
        this.compensado=Map.copyOf(compensado);this.devolvido=Map.copyOf(devolvido);
    }
}
