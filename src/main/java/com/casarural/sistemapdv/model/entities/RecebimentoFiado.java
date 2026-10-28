package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Retrato imutável do recebimento e das compras abatidas, obtido na transação que o gravou. */
public final class RecebimentoFiado {
    private final int idRecebimento, idCliente;
    private final String nomeCliente, telefone;
    private final LocalDateTime data;
    private final PaymentMethod forma;
    private final BigDecimal dividaAntes, valorPago;
    private final List<Compra> compras;

    public RecebimentoFiado(int idRecebimento, int idCliente, String nomeCliente, String telefone,
                            LocalDateTime data, PaymentMethod forma, BigDecimal dividaAntes,
                            BigDecimal valorPago, List<Compra> compras) {
        this.idRecebimento = idRecebimento;
        this.idCliente = idCliente;
        this.nomeCliente = nomeCliente;
        this.telefone = telefone;
        this.data = data;
        this.forma = forma;
        this.dividaAntes = Money.of(dividaAntes);
        this.valorPago = Money.of(valorPago);
        this.compras = List.copyOf(compras);
        BigDecimal soma = this.compras.stream().map(Compra::getValorPago).reduce(Money.ZERO, BigDecimal::add);
        if (idRecebimento <= 0 || idCliente <= 0 || data == null || forma == null || forma == PaymentMethod.FIADO
                || !Money.isPositive(this.valorPago) || this.valorPago.compareTo(this.dividaAntes) > 0
                || soma.compareTo(this.valorPago) != 0) {
            throw new IllegalArgumentException("Dados do recebimento de fiado inconsistentes.");
        }
    }

    public int getIdRecebimento() { return idRecebimento; }
    public int getIdCliente() { return idCliente; }
    public String getNomeCliente() { return nomeCliente; }
    public String getTelefone() { return telefone; }
    public LocalDateTime getData() { return data; }
    public PaymentMethod getForma() { return forma; }
    public BigDecimal getDividaAntes() { return dividaAntes; }
    public BigDecimal getValorPago() { return valorPago; }
    public BigDecimal getDividaDepois() { return dividaAntes.subtract(valorPago); }
    public List<Compra> getCompras() { return compras; }

    public static final class Compra {
        private final int idPedido;
        private final LocalDateTime data;
        private final BigDecimal valorCompra, desconto, saldoAntes, valorPago;
        private final List<Produto> produtos;

        public Compra(int idPedido, LocalDateTime data, BigDecimal valorCompra, BigDecimal desconto,
                      BigDecimal saldoAntes, BigDecimal valorPago, List<Produto> produtos) {
            this.idPedido = idPedido;
            this.data = data;
            this.valorCompra = Money.of(valorCompra);
            this.desconto = Money.of(desconto);
            this.saldoAntes = Money.of(saldoAntes);
            this.valorPago = Money.of(valorPago);
            this.produtos = List.copyOf(produtos);
            if (idPedido <= 0 || !Money.isPositive(this.valorPago) || this.valorPago.compareTo(this.saldoAntes) > 0
                    || this.saldoAntes.compareTo(this.valorCompra) > 0) {
                throw new IllegalArgumentException("Abatimento de fiado inconsistente.");
            }
        }

        public int getIdPedido() { return idPedido; }
        public LocalDateTime getData() { return data; }
        public BigDecimal getValorCompra() { return valorCompra; }
        public BigDecimal getDesconto() { return desconto; }
        public BigDecimal getPagoAntes() { return valorCompra.subtract(saldoAntes); }
        public BigDecimal getValorPago() { return valorPago; }
        public BigDecimal getSaldoDepois() { return saldoAntes.subtract(valorPago); }
        public boolean isQuitada() { return getSaldoDepois().signum() == 0; }
        public List<Produto> getProdutos() { return produtos; }
    }

    public static final class Produto {
        private final String nome;
        private final int quantidade;
        private final BigDecimal precoUnitario;

        public Produto(String nome, int quantidade, BigDecimal precoUnitario) {
            this.nome = nome == null ? "Produto indisponível" : nome;
            this.quantidade = quantidade;
            this.precoUnitario = Money.of(precoUnitario);
        }

        public String getNome() { return nome; }
        public int getQuantidade() { return quantidade; }
        public BigDecimal getPrecoUnitario() { return precoUnitario; }
        public BigDecimal getSubtotal() { return Money.of(precoUnitario.multiply(BigDecimal.valueOf(quantidade))); }
    }
}
