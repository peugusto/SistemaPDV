package com.casarural.sistemapdv.services.impressao;

import com.casarural.sistemapdv.model.entities.RegraListaCompras;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import static com.casarural.sistemapdv.services.impressao.ComprovanteVenda.*;

/** Captura as quantidades escolhidas antes de entrar na fila; imprimir não zera a pendência. */
public final class ComprovanteListaCompras implements Comprovante {
    private final String texto;
    private ComprovanteListaCompras(String texto) { this.texto = texto; }
    public String getTexto() { return texto; }
    public String getNomeTrabalho() { return "Casa Rural - Lista de compras"; }
    public static ComprovanteListaCompras gerar(List<Item> itens, LocalDateTime atualizadaEm) {
        List<Item> comprar = itens.stream().filter(i -> i.getComprar() > 0).sorted(RegraListaCompras.ORDEM).collect(Collectors.toList());
        if (comprar.isEmpty()) throw new IllegalArgumentException("Não há produtos com quantidade para imprimir.");
        List<String> linhas = new ArrayList<>();
        centralizar(linhas, "CASA RURAL"); centralizar(linhas, "LISTA DE COMPRAS");
        escrever(linhas, "Atualizada: " + atualizadaEm.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        separar(linhas);
        Boolean acabaram = null;
        for (Item item : comprar) {
            if (!Objects.equals(acabaram, item.acabou())) {
                if (acabaram != null) separar(linhas);
                acabaram = item.acabou(); escrever(linhas, acabaram ? "ACABARAM" : "REPOR");
            }
            escrever(linhas, "[ ] " + item.getComprar() + " x " + item.getProduto().getNomeProduto());
        }
        separar(linhas);
        valores(linhas, "PRODUTOS", String.valueOf(comprar.size()));
        valores(linhas, "UNIDADES PARA COMPRAR", String.valueOf(comprar.stream().mapToLong(Item::getComprar).sum()));
        escrever(linhas, "Dê entrada na mercadoria recebida para abater a lista.");
        return new ComprovanteListaCompras(String.join("\n", linhas) + "\n");
    }
}
