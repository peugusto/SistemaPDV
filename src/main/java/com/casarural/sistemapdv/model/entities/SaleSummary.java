package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.model.entities.enums.OrderStatus;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Uma linha do histórico de vendas: o essencial de um pedido, sem carregar itens e pagamentos. */
public class SaleSummary {
    private int idPedido;
    private LocalDateTime dataPedido;
    private BigDecimal valorTotal = Money.ZERO;
    private OrderStatus status;
    private String nomeCliente;
    private int quantidadeItens;
    private BigDecimal desconto = Money.ZERO;
    private String motivoCancelamento;
    /** Venda cancelada numa troca: a venda nova que a substituiu. */
    private Integer idPedidoTroca;
    /** Formas usadas na hora da venda (não inclui recebimentos de fiado posteriores). */
    private List<PaymentMethod> formas = new ArrayList<>();

    public static String nomeDaForma(PaymentMethod forma) {
        switch (forma) {
            case DINHEIRO: return "Dinheiro";
            case PIX: return "PIX";
            case CARTAO: return "Cartão";
            case FIADO: return "Fiado";
            default: return forma.toString();
        }
    }

    /** Ex.: "Dinheiro + Fiado". */
    public String getFormasTexto() {
        return formas.stream().map(SaleSummary::nomeDaForma).collect(Collectors.joining(" + "));
    }

    public int getIdPedido() { return idPedido; }
    public void setIdPedido(int idPedido) { this.idPedido = idPedido; }

    public LocalDateTime getDataPedido() { return dataPedido; }
    public void setDataPedido(LocalDateTime dataPedido) { this.dataPedido = dataPedido; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = Money.of(valorTotal); }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getNomeCliente() { return nomeCliente; }
    public void setNomeCliente(String nomeCliente) { this.nomeCliente = nomeCliente; }

    public int getQuantidadeItens() { return quantidadeItens; }
    public void setQuantidadeItens(int quantidadeItens) { this.quantidadeItens = quantidadeItens; }

    public BigDecimal getDesconto() { return desconto; }
    public void setDesconto(BigDecimal desconto) { this.desconto = Money.of(desconto); }

    public String getMotivoCancelamento() { return motivoCancelamento; }
    public void setMotivoCancelamento(String motivoCancelamento) { this.motivoCancelamento = motivoCancelamento; }

    public Integer getIdPedidoTroca() { return idPedidoTroca; }
    public void setIdPedidoTroca(Integer idPedidoTroca) { this.idPedidoTroca = idPedidoTroca; }

    public List<PaymentMethod> getFormas() { return formas; }
    public void setFormas(List<PaymentMethod> formas) { this.formas = formas; }
}
