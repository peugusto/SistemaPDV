package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import com.casarural.sistemapdv.util.SenhaPin;
import com.casarural.sistemapdv.db.DbException;
import java.time.Clock;

/**
 * PIN que protege os dashboards (faturamento é informação sensível).
 * Fica na tabela configuracao como hash; esqueceu o PIN: apagar a linha no Workbench
 * (chaves relatorios.pin e relatorios.pin.tentativas) e criar outro ao abrir.
 */
public class AcessoRelatoriosService {

    public static final String CHAVE_PIN = "relatorios.pin";
    public static final String CHAVE_TENTATIVAS = "relatorios.pin.tentativas";

    private final ConfiguracaoDao configuracao;
    private final Clock relogio;

    public AcessoRelatoriosService(ConfiguracaoDao configuracao) {
        this(configuracao, Clock.systemUTC());
    }

    public AcessoRelatoriosService(ConfiguracaoDao configuracao, Clock relogio) {
        this.configuracao = configuracao;
        this.relogio = relogio;
    }

    public boolean temPin() {
        return configuracao.get(CHAVE_PIN).isPresent();
    }

    public boolean confere(String pin) {
        String hash = configuracao.get(CHAVE_PIN).orElse(null);
        if (hash == null) return false;
        boolean[] autorizado = {false};
        configuracao.atualizar(CHAVE_TENTATIVAS, "0:0", valor -> {
            Estado estado = Estado.ler(valor);
            long agora = relogio.millis();
            if (estado.ate > agora) return valor;
            if (SenhaPin.confere(pin, hash)) {
                autorizado[0] = true;
                return "0:0";
            }
            int falhas = Math.min(estado.falhas + 1, 21);
            // A cada três erros: 30s, 60s, 120s... até 15 minutos, sem bloqueio permanente.
            long espera = falhas % 3 == 0 ? Math.min(900, 30L << (falhas / 3 - 1)) : 0;
            return falhas + ":" + (espera == 0 ? 0 : agora + espera * 1000);
        });
        return autorizado[0];
    }

    public long segundosParaNovaTentativa() {
        Estado estado = Estado.ler(configuracao.get(CHAVE_TENTATIVAS).orElse("0:0"));
        return Math.max(0, (estado.ate - relogio.millis() + 999) / 1000);
    }

    public void definirPin(String pin) {
        configuracao.set(CHAVE_PIN, SenhaPin.gerarHash(pin));
    }

    private static final class Estado {
        final int falhas;
        final long ate;
        Estado(int falhas, long ate) { this.falhas = falhas; this.ate = ate; }
        static Estado ler(String valor) {
            try {
                String[] partes = valor.split(":", -1);
                if (partes.length != 2) throw new IllegalArgumentException();
                int falhas = Integer.parseInt(partes[0]);
                long ate = Long.parseLong(partes[1]);
                if (falhas < 0 || falhas > 21 || ate < 0) throw new IllegalArgumentException();
                return new Estado(falhas, ate);
            } catch (IllegalArgumentException e) {
                throw new DbException("Não foi possível conferir o limite de tentativas de PIN. Peça ao responsável para verificar a configuração.", e);
            }
        }
    }
}
