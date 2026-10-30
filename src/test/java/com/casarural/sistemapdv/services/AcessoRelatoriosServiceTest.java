package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class AcessoRelatoriosServiceTest {
    static class ConfigMemoria implements ConfiguracaoDao {
        final Map<String, String> valores = new HashMap<>();
        public synchronized Optional<String> get(String c) { return Optional.ofNullable(valores.get(c)); }
        public synchronized void set(String c, String v) { valores.put(c, v); }
        public synchronized String atualizar(String c, String inicial, UnaryOperator<String> alteracao) {
            String novo = alteracao.apply(valores.getOrDefault(c, inicial));
            valores.put(c, novo);
            return novo;
        }
    }
    static class Relogio extends Clock {
        Instant agora = Instant.parse("2026-10-30T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zona) { return this; }
        public Instant instant() { return agora; }
        void avancar(long segundos) { agora = agora.plusSeconds(segundos); }
    }
    final ConfigMemoria config = new ConfigMemoria();
    final Relogio relogio = new Relogio();
    AcessoRelatoriosService novo() { return new AcessoRelatoriosService(config, relogio); }
    AcessoRelatoriosService preparar() {
        AcessoRelatoriosService s = novo();
        s.definirPin("4821");
        return s;
    }

    @Test void reabrirOuRecriarServicoNaoZeraErrosEBloqueiaMesmoPinCorreto() {
        preparar();
        for (int i = 0; i < 3; i++) assertFalse(novo().confere("0000"));
        assertEquals(30, novo().segundosParaNovaTentativa());
        assertFalse(novo().confere("4821"));
        relogio.avancar(29);
        assertEquals(1, novo().segundosParaNovaTentativa());
        relogio.avancar(1);
        assertTrue(novo().confere("4821"));
        assertEquals(0, novo().segundosParaNovaTentativa());
    }

    @Test void esperaProgrideAteQuinzeMinutosEAcertoReiniciaContagem() {
        AcessoRelatoriosService s = preparar();
        for (long esperado : new long[]{30, 60, 120, 240, 480, 900, 900}) {
            for (int i = 0; i < 3; i++) assertFalse(s.confere("0000"));
            assertEquals(esperado, s.segundosParaNovaTentativa());
            String antes = config.get(AcessoRelatoriosService.CHAVE_TENTATIVAS).orElseThrow();
            assertFalse(s.confere("0000"));
            assertEquals(antes, config.get(AcessoRelatoriosService.CHAVE_TENTATIVAS).orElseThrow());
            relogio.avancar(esperado);
        }
        assertTrue(s.confere("4821"));
        for (int i = 0; i < 3; i++) assertFalse(s.confere("0000"));
        assertEquals(30, s.segundosParaNovaTentativa());
    }

    @Test void acertoAntesDoLimiteLimpaErrosMasCancelarOuConsultarNaoLimpa() {
        AcessoRelatoriosService s = preparar();
        assertFalse(s.confere("0000"));
        assertFalse(s.confere("0000"));
        assertTrue(novo().temPin());
        assertEquals(0, novo().segundosParaNovaTentativa());
        assertTrue(novo().confere("4821"));
        assertFalse(novo().confere("0000"));
        assertEquals(0, s.segundosParaNovaTentativa());
    }

    @Test void tresTentativasConcorrentesNaoPerdemContagem() throws Exception {
        preparar();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Boolean>> tarefas = List.of(() -> novo().confere("0000"),
                    () -> novo().confere("0000"), () -> novo().confere("0000"));
            for (Future<Boolean> f : pool.invokeAll(tarefas)) assertFalse(f.get());
            assertEquals(30, novo().segundosParaNovaTentativa());
        } finally { pool.shutdownNow(); }
    }

    @Test void configuracaoInvalidaOuFalhaDePersistenciaNaoAutoriza() {
        AcessoRelatoriosService s = preparar();
        config.set(AcessoRelatoriosService.CHAVE_TENTATIVAS, "estragado");
        assertThrows(DbException.class, () -> s.confere("4821"));
        ConfigMemoria falha = new ConfigMemoria() {
            public String atualizar(String c, String inicial, UnaryOperator<String> alteracao) {
                alteracao.apply("0:0");
                throw new DbException("Falha simulada ao confirmar");
            }
        };
        AcessoRelatoriosService outro = new AcessoRelatoriosService(falha, relogio);
        outro.definirPin("4821");
        assertThrows(DbException.class, () -> outro.confere("4821"));
    }
}
