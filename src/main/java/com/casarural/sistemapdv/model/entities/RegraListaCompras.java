package com.casarural.sistemapdv.model.entities;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lista de compras da semana. Regra: comprar o que vendeu nos últimos 7 dias menos o que ainda tem
 * no estoque, para o estoque durar mais uma semana. Só produtos que controlam estoque (pão, açaí e
 * pesáveis ficam de fora).
 */
public final class RegraListaCompras {

    public static final int DIAS = 7;

    private RegraListaCompras() {}

    /** Um produto para repor. */
    public static final class Item {
        private final Product produto;
        private final int vendido;
        private final int estoque;
        private int comprar;

        Item(Product produto, int vendido, int estoque, int comprar) {
            this.produto = produto;
            this.vendido = vendido;
            this.estoque = estoque;
            this.comprar = comprar;
        }

        public Product getProduto() { return produto; }
        /** Quanto vendeu nos últimos 7 dias. */
        public int getVendido() { return vendido; }
        /** Quanto tem agora (negativo vira 0). */
        public int getEstoque() { return estoque; }
        /** Quanto comprar (sugerido; a pessoa pode mudar na tela). */
        public int getComprar() { return comprar; }
        public void setComprar(int comprar) { this.comprar = Math.max(0, comprar); }
        /** Já acabou: o mais urgente. */
        public boolean acabou() { return estoque == 0; }
    }

    /** Quanto comprar: o que vendeu na semana menos o que tem (nunca negativo). */
    public static int quantoComprar(int vendidoNaSemana, int estoqueAtual) {
        return Math.max(0, vendidoNaSemana - Math.max(0, estoqueAtual));
    }

    /**
     * Monta a lista a partir das vendas por produto dos últimos 7 dias.
     * Ordem: os que acabaram primeiro, depois pelo nome.
     */
    public static List<Item> montar(List<VendaProduto> vendasDaSemana) {
        List<Item> lista = new ArrayList<>();
        for (VendaProduto v : vendasDaSemana) {
            Product p = v.getProduto();
            if (!p.isControlaEstoque()) continue;              // pão, açaí, pesáveis
            int estoque = Math.max(0, p.getEstoque());
            int comprar = quantoComprar(v.getQuantidade(), estoque);
            if (comprar > 0) lista.add(new Item(p, v.getQuantidade(), estoque, comprar));
        }
        lista.sort(Comparator.comparing(Item::acabou).reversed()
                .thenComparing(i -> i.getProduto().getNomeProduto(), String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    /** Texto para copiar (WhatsApp, bloco de notas) e, no futuro, imprimir. Só itens com quantidade. */
    public static String texto(List<Item> itens, LocalDate hoje) {
        List<Item> comprar = itens.stream().filter(i -> i.getComprar() > 0)
                .sorted(Comparator.comparing(i -> i.getProduto().getNomeProduto(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("LISTA DE COMPRAS - Casa Rural - ")
                .append(hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append('\n');
        for (Item i : comprar) {
            sb.append("[ ] ").append(i.getComprar()).append(" x ").append(i.getProduto().getNomeProduto());
            if (i.acabou()) sb.append("  (ACABOU)");
            sb.append('\n');
        }
        sb.append(comprar.size()).append(comprar.size() == 1 ? " produto" : " produtos");
        return sb.toString();
    }
}
