package com.casarural.sistemapdv.db;

/** A chave não existia e toda esta tentativa foi desfeita; é seguro corrigir ou abandonar a entrada. */
public class EntradaEstoqueNaoAplicadaException extends DbException {
    public EntradaEstoqueNaoAplicadaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
