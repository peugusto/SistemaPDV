package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.entities.Product;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Regra da busca de produto (F2 no PDV), separada da tela para poder ser testada.
 * Ignora acentos e maiúsculas, e todas as palavras digitadas precisam aparecer
 * no nome ou no código: "acai 500" acha "Açaí 500 ml".
 */
public final class BuscaProduto {

    /** Códigos com até este tamanho são os "códigos curtos" (produtos sem código de barras). */
    public static final int TAMANHO_CODIGO_CURTO = 4;

    public enum Filtro {
        TODOS("Todos"),
        PESAVEIS("Pesáveis"),
        SEM_ESTOQUE_CONTROLADO("Sem controle de estoque"),
        CODIGOS_CURTOS("Códigos curtos");

        private final String texto;

        Filtro(String texto) { this.texto = texto; }

        @Override public String toString() { return texto; }
    }

    private BuscaProduto() {}

    public static boolean combina(Product p, String busca, Filtro filtro, boolean soComEstoque) {
        return passaNoFiltro(p, filtro) && passaNoEstoque(p, soComEstoque) && contemTexto(p, busca);
    }

    static boolean passaNoFiltro(Product p, Filtro filtro) {
        if (filtro == null) return true;
        return switch (filtro) {
            case TODOS -> true;
            case PESAVEIS -> p.isPesavel();
            case SEM_ESTOQUE_CONTROLADO -> !p.isControlaEstoque();
            case CODIGOS_CURTOS -> ehCodigoCurto(p);
        };
    }

    /** "Só com estoque" esconde quem controla estoque e está zerado; pesável e sem controle sempre aparecem. */
    static boolean passaNoEstoque(Product p, boolean soComEstoque) {
        return !soComEstoque || p.temEstoquePara(1);
    }

    static boolean contemTexto(Product p, String busca) {
        String b = normalizar(busca);
        if (b.isEmpty()) return true;
        String alvo = normalizar(p.getNomeProduto()) + " " + normalizar(p.getCodBarras());
        for (String palavra : b.split("\\s+")) {
            if (!alvo.contains(palavra)) return false;
        }
        return true;
    }

    public static boolean ehCodigoCurto(Product p) {
        return p.getCodBarras() != null && p.getCodBarras().trim().length() <= TAMANHO_CODIGO_CURTO;
    }

    /** Minúsculas e sem acento: "Açaí" vira "acai". */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).trim();
    }
}
