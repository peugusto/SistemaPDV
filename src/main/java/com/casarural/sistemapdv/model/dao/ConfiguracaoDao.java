package com.casarural.sistemapdv.model.dao;

import java.util.Optional;

/** Tabela configuracao (V12): pares chave/valor do sistema. */
public interface ConfiguracaoDao {

    Optional<String> get(String chave);

    /** Cria ou troca o valor. */
    void set(String chave, String valor);
}
