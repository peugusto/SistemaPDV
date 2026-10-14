package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import com.casarural.sistemapdv.util.Tema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Acessibilidade: guardar e ler tamanho da letra, cores e foco. */
class PreferenciasTelaTest {

    /** Configuração em memória (sem banco). */
    static class ConfigFalsa implements ConfiguracaoDao {
        final Map<String, String> valores = new HashMap<>();
        public Optional<String> get(String chave) { return Optional.ofNullable(valores.get(chave)); }
        public void set(String chave, String valor) { valores.put(chave, valor); }
    }

    @AfterEach
    void voltarAoPadrao() {
        Tema.configurar(Tema.Cores.CLARO, Tema.LETRA_NORMAL, false);
    }

    @Test
    void semNadaSalvoFicaOPadrao() {
        new PreferenciasTelaService(new ConfigFalsa()).carregar();
        assertEquals(Tema.LETRA_NORMAL, Tema.getTamanhoLetra());
        assertEquals(Tema.Cores.CLARO, Tema.getCores());
        assertFalse(Tema.isFocoDestacado());
    }

    @Test
    void salvaELeDeVolta() {
        ConfigFalsa config = new ConfigFalsa();
        PreferenciasTelaService prefs = new PreferenciasTelaService(config);
        Tema.configurar(Tema.Cores.ALTO_CONTRASTE, Tema.LETRA_MUITO_GRANDE, true);
        prefs.salvar();
        Tema.configurar(Tema.Cores.CLARO, Tema.LETRA_NORMAL, false);

        prefs.carregar();
        assertEquals(Tema.LETRA_MUITO_GRANDE, Tema.getTamanhoLetra());
        assertEquals(Tema.Cores.ALTO_CONTRASTE, Tema.getCores());
        assertTrue(Tema.isFocoDestacado());
    }

    @Test
    void valorEstragadoNoBancoVoltaAoPadrao() {
        ConfigFalsa config = new ConfigFalsa();
        config.set(PreferenciasTelaService.CHAVE_LETRA, "enorme");
        config.set(PreferenciasTelaService.CHAVE_CORES, "ROXO");
        new PreferenciasTelaService(config).carregar();
        assertEquals(Tema.LETRA_NORMAL, Tema.getTamanhoLetra());
        assertEquals(Tema.Cores.CLARO, Tema.getCores());
    }

    @Test
    void letraTemLimites() {
        Tema.configurar(Tema.Cores.CLARO, 99, false);
        assertEquals(Tema.LETRA_MAXIMA, Tema.getTamanhoLetra());
        Tema.configurar(Tema.Cores.CLARO, 3, false);
        assertEquals(Tema.LETRA_MINIMA, Tema.getTamanhoLetra());
    }
}
