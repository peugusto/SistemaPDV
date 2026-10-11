package com.casarural.sistemapdv.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lê o manual (resources/.../ajuda/manual.txt), um texto simples com marcações no começo da linha:
 * "# " assunto, "## " subtítulo, "- " item, "1. " passo, "! " aviso, "> " mensagem da tela,
 * ";;" comentário. Linhas comuns seguidas viram um parágrafo; linha vazia separa.
 * Assim o manual muda sem mexer em código.
 */
public final class Manual {

    public static final String ARQUIVO = "/com/casarural/sistemapdv/ajuda/manual.txt";

    public enum TipoBloco { SUBTITULO, PARAGRAFO, ITEM, PASSO, AVISO, MENSAGEM }

    public static final class Bloco {
        private final TipoBloco tipo;
        private final String texto;
        /** Número do passo (só em PASSO). */
        private final int numero;

        Bloco(TipoBloco tipo, String texto, int numero) {
            this.tipo = tipo;
            this.texto = texto;
            this.numero = numero;
        }

        public TipoBloco getTipo() { return tipo; }
        public String getTexto() { return texto; }
        public int getNumero() { return numero; }
    }

    public static final class Secao {
        private final String titulo;
        private final List<Bloco> blocos = new ArrayList<>();

        Secao(String titulo) { this.titulo = titulo; }

        public String getTitulo() { return titulo; }
        public List<Bloco> getBlocos() { return Collections.unmodifiableList(blocos); }

        /** Título e todo o texto, sem acento e em minúsculas, para a busca. */
        public boolean contem(String busca) {
            String b = normalizar(busca);
            if (b.isEmpty()) return true;
            StringBuilder tudo = new StringBuilder(normalizar(titulo));
            for (Bloco bloco : blocos) tudo.append(' ').append(normalizar(bloco.texto));
            String alvo = tudo.toString();
            for (String palavra : b.split("\\s+")) {
                if (!alvo.contains(palavra)) return false;
            }
            return true;
        }

        @Override public String toString() { return titulo; }
    }

    private Manual() {}

    public static List<Secao> carregar() {
        try (InputStream in = Manual.class.getResourceAsStream(ARQUIVO)) {
            if (in == null) throw new IllegalStateException("Manual não encontrado: " + ARQUIVO);
            return ler(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler o manual", e);
        }
    }

    public static List<Secao> ler(String texto) {
        List<Secao> secoes = new ArrayList<>();
        Secao atual = null;
        StringBuilder paragrafo = new StringBuilder();

        for (String bruta : texto.split("\\R", -1)) {
            String linha = bruta.strip();
            if (linha.startsWith(";;")) continue;

            boolean comum = !linha.isEmpty() && !linha.startsWith("#") && !linha.startsWith("- ")
                    && !linha.startsWith("! ") && !linha.startsWith("> ") && !linha.matches("\\d+\\.\\s.*");
            if (!comum) fecharParagrafo(atual, paragrafo);
            if (linha.isEmpty()) continue;

            if (linha.startsWith("## ")) {
                atual = garantir(secoes, atual);
                atual.blocos.add(new Bloco(TipoBloco.SUBTITULO, linha.substring(3).strip(), 0));
            } else if (linha.startsWith("# ")) {
                atual = new Secao(linha.substring(2).strip());
                secoes.add(atual);
            } else if (linha.startsWith("- ")) {
                atual = garantir(secoes, atual);
                atual.blocos.add(new Bloco(TipoBloco.ITEM, linha.substring(2).strip(), 0));
            } else if (linha.startsWith("! ")) {
                atual = garantir(secoes, atual);
                atual.blocos.add(new Bloco(TipoBloco.AVISO, linha.substring(2).strip(), 0));
            } else if (linha.startsWith("> ")) {
                atual = garantir(secoes, atual);
                atual.blocos.add(new Bloco(TipoBloco.MENSAGEM, linha.substring(2).strip(), 0));
            } else if (linha.matches("\\d+\\.\\s.*")) {
                atual = garantir(secoes, atual);
                int ponto = linha.indexOf('.');
                atual.blocos.add(new Bloco(TipoBloco.PASSO, linha.substring(ponto + 1).strip(),
                        Integer.parseInt(linha.substring(0, ponto))));
            } else {
                if (paragrafo.length() > 0) paragrafo.append(' ');
                paragrafo.append(linha);
            }
        }
        fecharParagrafo(atual, paragrafo);
        return secoes;
    }

    private static Secao garantir(List<Secao> secoes, Secao atual) {
        if (atual != null) return atual;
        Secao s = new Secao("Introdução");
        secoes.add(s);
        return s;
    }

    private static void fecharParagrafo(Secao atual, StringBuilder paragrafo) {
        if (paragrafo.length() == 0) return;
        if (atual != null) atual.blocos.add(new Bloco(TipoBloco.PARAGRAFO, paragrafo.toString(), 0));
        paragrafo.setLength(0);
    }

    static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
