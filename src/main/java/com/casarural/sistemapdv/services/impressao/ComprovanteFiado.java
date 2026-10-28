package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.util.Money;
import com.casarural.sistemapdv.util.Telefone;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;

public final class ComprovanteFiado implements Comprovante {
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final int idRecebimento;
    private final String texto;

    private ComprovanteFiado(int idRecebimento, String texto) {
        this.idRecebimento = idRecebimento;
        this.texto = texto;
    }

    public String getTexto() { return texto; }
    public String getNomeTrabalho() { return "Casa Rural - Recebimento de fiado #" + idRecebimento; }

    public static ComprovanteFiado gerar(RecebimentoFiado recebimento) {
        List<String> linhas = new ArrayList<>();
        centralizar(linhas, "CASA RURAL");
        centralizar(linhas, "PAGAMENTO DE FIADO");
        centralizar(linhas, "NÃO É DOCUMENTO FISCAL");
        escrever(linhas, "Cliente: " + recebimento.getNomeCliente());
        if (recebimento.getTelefone() != null && !recebimento.getTelefone().isBlank()) {
            escrever(linhas, "Telefone: " + Telefone.formatar(recebimento.getTelefone()));
        }
        String dataAnterior = null;
        for (RecebimentoFiado.Compra compra : recebimento.getCompras()) {
            String data = compra.getData() == null ? "SEM DATA" : DIA.format(compra.getData());
            if (!data.equals(dataAnterior)) {
                String titulo = " " + data + " ";
                int esquerda = (COLUNAS - titulo.length()) / 2;
                linhas.add("-".repeat(esquerda) + titulo + "-".repeat(COLUNAS - esquerda - titulo.length()));
                dataAnterior = data;
            }
            for (RecebimentoFiado.Produto produto : compra.getProdutos()) {
                // Mantém quantidade, nome e valor na mesma linha sempre que couber.
                List<String> nome = new ArrayList<>();
                escrever(nome, produto.getQuantidade() + " x " + produto.getNome());
                String ultima = nome.remove(nome.size() - 1);
                linhas.addAll(nome);
                valores(linhas, ultima, Money.format(produto.getSubtotal()));
            }
            if (compra.getProdutos().isEmpty()) escrever(linhas, "Produtos não disponíveis.");
        }
        separar(linhas);
        valores(linhas, "TOTAL DO FIADO", Money.format(recebimento.getDividaAntes()));
        valores(linhas, "PAGO AGORA", Money.format(recebimento.getValorPago()));
        valores(linhas, "RESTANTE", Money.format(recebimento.getDividaDepois()));
        separar(linhas);
        centralizar(linhas, "Obrigado pela preferência!");
        return new ComprovanteFiado(recebimento.getIdRecebimento(), String.join("\n", linhas) + "\n");
    }
}
