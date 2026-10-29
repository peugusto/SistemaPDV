package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Uma venda já gravada, com o necessário para cancelar ou trocar. */
public class VendaParaDesfazer {

    private int idPedido;
    private Integer idCliente;
    private OrderStatus status;
    private LocalDateTime dataPedido;
    private BigDecimal valorTotal = Money.ZERO;
    private BigDecimal desconto = Money.ZERO;
    /** Itens com o produto completo (para voltarem ao cupom na troca). */
    private final List<OrderItem> itens = new ArrayList<>();
    /** Quanto foi pago em cada forma (inclusive recebimentos depois); FIADO = o que ainda ficou na conta. */
    private final Map<PaymentMethod, BigDecimal> pagoPorForma = new EnumMap<>(PaymentMethod.class);
    /** A venda foi feita no caixa que está aberto agora (só essas podem ser desfeitas). */
    private boolean doCaixaAberto;

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getDataPedido() { return dataPedido; }
    public void setDataPedido(LocalDateTime dataPedido) { this.dataPedido = dataPedido; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = Money.of(valorTotal); }

    public BigDecimal getDesconto() { return desconto; }
    public void setDesconto(BigDecimal desconto) { this.desconto = Money.of(desconto); }

    public List<OrderItem> getItens() { return itens; }

    public Map<PaymentMethod, BigDecimal> getPagoPorForma() { return pagoPorForma; }

    public boolean isDoCaixaAberto() { return doCaixaAberto; }
    public void setDoCaixaAberto(boolean doCaixaAberto) { this.doCaixaAberto = doCaixaAberto; }

    public boolean isCancelada() { return status == OrderStatus.CANCELADO; }

    /** Pode cancelar ou trocar: não está cancelada e é do caixa aberto. */
    public boolean podeDesfazer() { return !isCancelada() && doCaixaAberto && temBaixaConhecida(); }

    public boolean temBaixaConhecida() { return !itens.isEmpty() && itens.stream().allMatch(i -> i.getEstoqueBaixado() != null); }
    /** O que voltaria para a mão do cliente ao cancelar (tudo menos o fiado). */
    public Map<PaymentMethod, BigDecimal> getDevolverAoCancelar() {
        Map<PaymentMethod, BigDecimal> m = new EnumMap<>(PaymentMethod.class);
        pagoPorForma.forEach((forma, valor) -> {
            if (forma != PaymentMethod.FIADO && Money.isPositive(valor)) m.put(forma, valor);
        });
        return m;
    }
}
