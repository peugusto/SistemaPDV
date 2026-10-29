package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Telefone;
import java.util.ArrayList;
import java.util.List;
import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;
import static com.casarural.sistemapdv.services.impressao.LinhasExtrato.*;

/** Extrato informativo: nenhuma chamada de recebimento ou compensação. */
public final class ComprovanteDivida implements Comprovante {
    private final int idCliente;
    private final String texto;
    private ComprovanteDivida(int idCliente, String texto) { this.idCliente = idCliente; this.texto = texto; }
    public String getTexto() { return texto; }
    public String getNomeTrabalho() { return "Casa Rural - Conta de fiado #" + idCliente; }
    public static ComprovanteDivida gerar(ExtratoFiado extrato) {
        List<String> linhas = new ArrayList<>();
        centralizar(linhas, "CASA RURAL"); centralizar(linhas, "EXTRATO DE FIADO"); centralizar(linhas, "NÃO É DOCUMENTO FISCAL");
        separar(linhas);
        Customer c = extrato.getCliente();
        escrever(linhas, "Cliente #" + c.getIdCliente() + ": " + c.getNomeCliente());
        if (c.getTelefone() != null && !c.getTelefone().isBlank()) escrever(linhas, "Telefone: " + Telefone.formatar(c.getTelefone()));
        escrever(linhas, "Emitido: " + data(extrato.getEmitidoEm(), "dd/MM/yyyy HH:mm"));
        escrever(linhas, "Compras com saldo em aberto:");
        String anterior = null;
        for (Order compra : extrato.getCompras()) {
            String dia = data(compra.getDataPedido(), "dd/MM/yyyy");
            if (!dia.equals(anterior)) { dia(linhas, dia); anterior = dia; }
            for (OrderItem item : compra.getItemPedido()) produto(linhas, item);
            if (compra.getItemPedido().isEmpty()) escrever(linhas, "Produtos não disponíveis para a compra #" + compra.getIdPedido());
        }
        if (extrato.getCompras().isEmpty()) escrever(linhas, "Nenhuma dívida de fiado em aberto.");
        separar(linhas);
        if (Money.isPositive(extrato.getTotalDescontos())) {
            valores(linhas, "Subtotal dos produtos", Money.format(extrato.getTotalCompras().add(extrato.getTotalDescontos())));
            valores(linhas, "Descontos", "- " + Money.format(extrato.getTotalDescontos()));
        }
        valores(linhas, "TOTAL DAS COMPRAS", Money.format(extrato.getTotalCompras()));
        valores(linhas, "JÁ PAGO NESSAS COMPRAS", Money.format(extrato.getTotalPago()));
        valores(linhas, "TOTAL DEVENDO", Money.format(extrato.getTotalDevendo()));
        separar(linhas); centralizar(linhas, "Obrigado pela preferência!");
        return new ComprovanteDivida(c.getIdCliente(), String.join("\n", linhas) + "\n");
    }
}
