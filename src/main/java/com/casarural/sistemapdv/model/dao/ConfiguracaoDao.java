package com.casarural.sistemapdv.model.dao;

import java.util.Optional;
import java.util.function.UnaryOperator;

/** Tabela configuracao (V12): pares chave/valor do sistema. */
public interface ConfiguracaoDao {

    Optional<String> get(String chave);

    /** Cria ou troca o valor. */
    void set(String chave, String valor);

    /** Lê e modifica um valor na mesma transação, serializando escritores desta chave. */
    String atualizar(String chave, String inicial, UnaryOperator<String> alteracao);
}
