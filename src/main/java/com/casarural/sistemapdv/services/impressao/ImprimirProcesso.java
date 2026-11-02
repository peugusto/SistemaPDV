package com.casarural.sistemapdv.services.impressao;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Processo descartável: recebe somente o documento, sem acesso a configuração ou banco. */
public final class ImprimirProcesso {
    private static final int MAXIMO_BYTES = 2_000_000;
    private ImprimirProcesso() {}

    static void gravar(OutputStream saida, Comprovante documento) throws IOException {
        byte[] texto = documento.getTexto().getBytes(StandardCharsets.UTF_8);
        if (texto.length < 1 || texto.length > MAXIMO_BYTES || documento.getNomeTrabalho().length() > 512) throw new IOException("Comprovante excede o tamanho permitido.");
        DataOutputStream out = new DataOutputStream(saida);
        out.writeInt(1);out.writeUTF(documento.getNomeTrabalho());out.writeInt(texto.length);out.write(texto);out.flush();
    }

    static Comprovante ler(InputStream entrada) throws IOException {
        DataInputStream in = new DataInputStream(entrada);
        if (in.readInt() != 1) throw new IOException("Documento incompatível.");
        String nome = in.readUTF();int tamanho = in.readInt();
        if (nome.length() > 512 || tamanho < 1 || tamanho > MAXIMO_BYTES) throw new IOException("Documento inválido.");
        byte[] dados = new byte[tamanho];in.readFully(dados);
        String texto = new String(dados,StandardCharsets.UTF_8);
        return new Comprovante() {
            public String getNomeTrabalho() { return nome; }
            public String getTexto() { return texto; }
        };
    }

    public static void main(String[] args) {
        if (args.length != 1) { System.exit(2); return; }
        try {
            if (args[0].isBlank()) throw new IOException("Impressora inválida.");
            Comprovante documento = ler(System.in);
            new ImpressoraTermica(args[0]).enviar(documento);
        } catch (Exception e) {
            // O pai usa o código de saída. Nunca grava produtos/clientes em arquivos temporários ou logs.
            System.exit(1);
        }
    }
}
