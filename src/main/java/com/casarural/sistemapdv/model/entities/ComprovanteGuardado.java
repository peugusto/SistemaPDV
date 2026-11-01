package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.services.impressao.Comprovante;
import java.util.Objects;

/** Conteúdo imutável, sem referências aos objetos da tela. */
public final class ComprovanteGuardado implements Comprovante {
    public enum Tipo { VENDA, FIADO, LISTA_COMPRAS }
    private final Tipo tipo;
    private final String referencia, nome, texto;
    public ComprovanteGuardado(Tipo tipo, String referencia, Comprovante original) {
        this(tipo, referencia, original.getNomeTrabalho(), original.getTexto());
    }
    public ComprovanteGuardado(Tipo tipo, String referencia, String nome, String texto) {
        this.tipo = Objects.requireNonNull(tipo);
        this.referencia = Objects.requireNonNull(referencia);
        this.nome = Objects.requireNonNull(nome);
        this.texto = Objects.requireNonNull(texto);
        if (referencia.isBlank() || referencia.length() > 50 || nome.length() > 180 || texto.isBlank())
            throw new IllegalArgumentException("Comprovante inválido.");
    }
    public Tipo getTipo() { return tipo; }
    public String getReferencia() { return referencia; }
    public String getNomeTrabalho() { return nome; }
    public String getTexto() { return texto; }
}
