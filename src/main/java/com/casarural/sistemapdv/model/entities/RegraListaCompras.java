package com.casarural.sistemapdv.model.entities;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Lista acumulada de reposição e apresentação. O cálculo semanal legado é mantido como referência da migração inicial. */
public final class RegraListaCompras {

    public static final int DIAS = 7;
    public static final Comparator<Item> ORDEM = Comparator.comparing(Item::acabou).reversed()
            .thenComparing(i -> i.getProduto().getNomeProduto(), String.CASE_INSENSITIVE_ORDER);

    private RegraListaCompras() {}

    /** Um produto para repor. */
    public static final class Item {
        private final Product produto;
        private final int vendido;
        private final int estoque;
        private final int pendente;
        /** Observável: a coluna de custo da tela acompanha quando a pessoa muda a quantidade. */
        private final javafx.beans.property.IntegerProperty comprar = new javafx.beans.property.SimpleIntegerProperty();

        Item(Product produto, int vendido, int estoque, int comprar) {
            this.produto = produto;
            this.vendido = vendido;
            this.estoque = estoque;
            this.pendente = comprar;
            this.comprar.set(comprar);
        }

        public Product getProduto() { return produto; }
        /** Quanto vendeu nos últimos 7 dias. */
        public int getVendido() { return vendido; }
        /** Saídas que ainda não foram repostas; não expiram com o tempo. */
        public int getPendente() { return pendente; }
        /** Quanto tem agora (negativo vira 0). */
        public int getEstoque() { return estoque; }
        /** Quanto comprar (sugerido; a pessoa pode mudar na tela). */
        public int getComprar() { return comprar.get(); }
        public void setComprar(int comprar) { this.comprar.set(Math.max(0, comprar)); }
        public javafx.beans.property.IntegerProperty comprarProperty() { return comprar; }
        /** Custo estimado desta linha (comprar x último custo); null sem custo. */
        public java.math.BigDecimal getCustoEstimado() {
            java.math.BigDecimal c = produto.getPrecoCusto();
            return c == null ? null : c.multiply(java.math.BigDecimal.valueOf(getComprar()));
        }

        /** Já acabou: o mais urgente. */
        public boolean acabou() { return estoque == 0; }
    }

    /** Quantidade acumulada persistida, independente da previsão semanal antiga. */
    public static Item acumulado(Product produto, int quantidade) {
        if (quantidade < 0) throw new IllegalArgumentException("A quantidade pendente não pode ser negativa.");
        return new Item(produto, 0, Math.max(0, produto.getEstoque()), quantidade);
    }
    /** Quanto comprar: o que vendeu na semana menos o que tem (nunca negativo). */
    public static int quantoComprar(int vendidoNaSemana, int estoqueAtual) {
        return (int)Math.max(0L, (long)vendidoNaSemana - Math.max(0, estoqueAtual));
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
            if (!p.isAtivo()) continue;                        // desativado: não se compra mais
            int estoque = Math.max(0, p.getEstoque());
            int comprar = quantoComprar(v.getQuantidade(), estoque);
            if (comprar > 0) lista.add(new Item(p, v.getQuantidade(), estoque, comprar));
        }
        lista.sort(Comparator.comparing(Item::acabou).reversed()
                .thenComparing(i -> i.getProduto().getNomeProduto(), String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    /** Texto para copiar (WhatsApp, bloco de notas). Só itens com quantidade. */
    public static String texto(List<Item> itens, LocalDate hoje) {
        List<Item> comprar = itens.stream().filter(i -> i.getComprar() > 0)
                .sorted(ORDEM)
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
