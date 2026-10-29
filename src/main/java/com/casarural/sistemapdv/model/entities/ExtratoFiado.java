package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Dados de leitura obtidos juntos; não registra pagamento nem altera a conta. */
public final class ExtratoFiado {
    private final Customer cliente;
    private final LocalDateTime emitidoEm;
    private final List<Order> compras;

    public ExtratoFiado(Customer cliente, LocalDateTime emitidoEm, List<Order> compras) {
        this.cliente = cliente;
        this.emitidoEm = emitidoEm;
        this.compras = List.copyOf(compras);
    }
    public Customer getCliente() { return cliente; }
    public LocalDateTime getEmitidoEm() { return emitidoEm; }
    public List<Order> getCompras() { return compras; }
    public BigDecimal getTotalCompras() { return compras.stream().map(Order::getValorTotal).reduce(Money.ZERO, BigDecimal::add); }
    public BigDecimal getTotalPago() { return compras.stream().map(c -> c.getPagoNaVenda().add(c.getRecebidoDepois())).reduce(Money.ZERO, BigDecimal::add); }
    public BigDecimal getTotalDescontos() { return compras.stream().map(Order::getDesconto).reduce(Money.ZERO, BigDecimal::add); }
    public BigDecimal getTotalDevendo() { return compras.stream().map(Order::getFalta).reduce(Money.ZERO, BigDecimal::add); }
}
