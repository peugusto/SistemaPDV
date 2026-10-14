package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import com.casarural.sistemapdv.util.Tema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guarda as escolhas do menu Acessibilidade (tamanho da letra, cores, foco destacado)
 * na tabela configuracao, para valerem na próxima vez que o sistema abrir.
 */
public class PreferenciasTelaService {

    private static final Logger log = LoggerFactory.getLogger(PreferenciasTelaService.class);
    static final String CHAVE_LETRA = "tela.letra";
    static final String CHAVE_CORES = "tela.cores";
    static final String CHAVE_FOCO = "tela.foco";

    private final ConfiguracaoDao configuracao;

    public PreferenciasTelaService(ConfiguracaoDao configuracao) {
        this.configuracao = configuracao;
    }

    /** Lê o que estava salvo e passa para o Tema. Sem banco ou valor estranho: fica o padrão. */
    public void carregar() {
        try {
            int letra = configuracao.get(CHAVE_LETRA).map(PreferenciasTelaService::numero).orElse(Tema.LETRA_NORMAL);
            Tema.Cores cores = configuracao.get(CHAVE_CORES).map(PreferenciasTelaService::cores).orElse(Tema.Cores.CLARO);
            boolean foco = configuracao.get(CHAVE_FOCO).map("sim"::equals).orElse(false);
            Tema.configurar(cores, letra, foco);
        } catch (RuntimeException e) {
            log.warn("Não foi possível ler as preferências de tela; usando o padrão", e);
        }
    }

    public void salvar() {
        try {
            configuracao.set(CHAVE_LETRA, String.valueOf(Tema.getTamanhoLetra()));
            configuracao.set(CHAVE_CORES, Tema.getCores().name());
            configuracao.set(CHAVE_FOCO, Tema.isFocoDestacado() ? "sim" : "nao");
        } catch (RuntimeException e) {
            log.warn("Não foi possível salvar as preferências de tela", e);
        }
    }

    static int numero(String texto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return Tema.LETRA_NORMAL;
        }
    }

    static Tema.Cores cores(String texto) {
        try {
            return Tema.Cores.valueOf(texto.trim());
        } catch (IllegalArgumentException e) {
            return Tema.Cores.CLARO;
        }
    }
}
