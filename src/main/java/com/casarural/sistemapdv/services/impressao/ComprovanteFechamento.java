package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.util.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;
import static com.casarural.sistemapdv.services.impressao.LinhasExtrato.*;

public final class ComprovanteFechamento implements Comprovante {
    private final int idCaixa;
    private final String texto;
    private ComprovanteFechamento(int idCaixa, String texto) { this.idCaixa = idCaixa; this.texto = texto; }
    public String getTexto() { return texto; }
    public String getNomeTrabalho() { return "Casa Rural - Fechamento do caixa #" + idCaixa; }
    public static ComprovanteFechamento gerar(FechamentoCaixa fechamento) {
        CashRegister caixa = fechamento.getCaixa();
        if (caixa.isAberto()) throw new IllegalArgumentException("O relatório exige um caixa fechado.");
        List<String> linhas = new ArrayList<>();
        centralizar(linhas, "CASA RURAL"); centralizar(linhas, "FECHAMENTO DE CAIXA");
        escrever(linhas, "Caixa #" + caixa.getIdCaixa());
        escrever(linhas, "Abertura: " + data(caixa.getAbertoEm(), "dd/MM/yyyy HH:mm"));
        escrever(linhas, "Fechado: " + data(caixa.getFechadoEm(), "dd/MM/yyyy HH:mm"));
        separar(linhas); escrever(linhas, "VENDAS");
        for (Order venda : fechamento.getVendas()) {
            separar(linhas);
            escrever(linhas, data(venda.getDataPedido(), "HH:mm") + " Venda #" + venda.getIdPedido());
            if (venda.getCostumer() != null) escrever(linhas, "Cliente: " + venda.getCostumer().getNomeCliente());
            for (OrderItem item : venda.getItemPedido()) produto(linhas, item);
            if (venda.getStatus() == OrderStatus.CANCELADO) {
                escrever(linhas, "CANCELADA / TROCADA - fora do total");
            } else {
                if (Money.isPositive(venda.getDesconto())) valores(linhas, "Desconto", "- " + Money.format(venda.getDesconto()));
                valores(linhas, "Total da venda", Money.format(venda.getValorTotal()));
                for (PaymentMethod forma : PaymentMethod.values()) {
                    BigDecimal valor = venda.getTotalPagamentos(forma);
                    if (Money.isPositive(valor)) valores(linhas, nome(forma), Money.format(valor));
                }
            }
        }
        if (fechamento.getVendas().isEmpty()) escrever(linhas, "Nenhuma venda neste caixa.");
        separar(linhas); escrever(linhas, "RETIRADAS E REFORÇOS");
        for (CashMovement m : fechamento.getMovimentos()) {
            String tipo = m.getTipo() == CashMovementType.SANGRIA ? "Retirada" : "Reforço";
            valores(linhas, data(m.getDataMovimento(), "HH:mm") + " " + tipo + " #" + m.getIdMovimento(), Money.format(m.getValor()));
            if (m.getMotivo() != null && !m.getMotivo().isBlank()) escrever(linhas, "Motivo: " + m.getMotivo());
        }
        if (fechamento.getMovimentos().isEmpty()) escrever(linhas, "Nenhuma retirada ou reforço.");
        if (!fechamento.getRecebimentos().isEmpty()) {
            separar(linhas); escrever(linhas, "RECEBIMENTOS DE FIADO");
            for (Payment pg : fechamento.getRecebimentos()) {
                escrever(linhas, data(pg.getDataPagamento(), "HH:mm") + " Compra #" + pg.getOrder().getIdPedido());
                if (pg.getOrder().getCostumer() != null) escrever(linhas, pg.getOrder().getCostumer().getNomeCliente());
                valores(linhas, nome(pg.getMetodo()), Money.format(pg.getValorPago()));
            }
        }
        CashSummary r = fechamento.getResumo();
        separar(linhas); escrever(linhas, "RESUMO DO CAIXA");
        for (PaymentMethod forma : PaymentMethod.values()) valores(linhas, "Vendas - " + nome(forma), Money.format(r.getVendas(forma)));
        valores(linhas, "TOTAL VENDIDO", Money.format(r.getTotalVendido()));
        for (PaymentMethod forma : new PaymentMethod[]{PaymentMethod.DINHEIRO, PaymentMethod.PIX, PaymentMethod.CARTAO}) {
            if (Money.isPositive(r.getRecebimentosFiado(forma))) valores(linhas, "Fiado recebido - " + nome(forma), Money.format(r.getRecebimentosFiado(forma)));
        }
        valores(linhas, "TOTAL FIADO RECEBIDO", Money.format(r.getTotalFiadoRecebido()));
        separar(linhas);
        valores(linhas, "Troco inicial", Money.format(r.getValorInicial()));
        valores(linhas, "Reforços", Money.format(r.getReforcos()));
        valores(linhas, "TOTAL RETIRADO", Money.format(r.getSangrias()));
        BigDecimal esperado = caixa.getValorEsperado() == null ? r.getDinheiroEsperado() : caixa.getValorEsperado();
        valores(linhas, "ESPERADO NA GAVETA", Money.format(esperado));
        if (caixa.getValorContado() == null) escrever(linhas, "Gaveta não conferida.");
        else {
            valores(linhas, "CONTADO", Money.format(caixa.getValorContado()));
            valores(linhas, "DIFERENÇA (contado - esperado)", Money.format(caixa.getValorContado().subtract(esperado)));
        }
        if (esperado.compareTo(r.getDinheiroEsperado()) != 0) {
            valores(linhas, "Saldo pelos lançamentos", Money.format(r.getDinheiroEsperado()));
            escrever(linhas, "ATENÇÃO: lançamentos divergem do fechamento. Confira o caixa.");
        }
        if (caixa.getObservacao() != null && !caixa.getObservacao().isBlank()) escrever(linhas, "Observação: " + caixa.getObservacao());
        separar(linhas); centralizar(linhas, "Fim do fechamento");
        return new ComprovanteFechamento(caixa.getIdCaixa(), String.join("\n", linhas) + "\n");
    }
}
