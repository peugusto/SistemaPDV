package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.util.Money;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;

final class LinhasExtrato {
    private LinhasExtrato() {}
    static String data(LocalDateTime valor, String padrao) {
        return valor == null ? "Sem data" : DateTimeFormatter.ofPattern(padrao).format(valor);
    }
    static void dia(List<String> linhas, String data) {
        String titulo = " " + data + " ";
        int esquerda = Math.max(0, (COLUNAS - titulo.length()) / 2);
        linhas.add("-".repeat(esquerda) + titulo + "-".repeat(Math.max(0, COLUNAS - esquerda - titulo.length())));
    }
    static void produto(List<String> linhas, OrderItem item) {
        List<String> nome = new ArrayList<>();
        escrever(nome, item.getQtd() + " x " + item.getNomeProduto());
        String ultima = nome.remove(nome.size() - 1);
        linhas.addAll(nome);
        valores(linhas, ultima, Money.format(item.getSubtotal()));
    }
}
