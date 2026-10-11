package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ConfiguracaoDao;
import com.casarural.sistemapdv.util.SenhaPin;

/**
 * PIN que protege os dashboards (faturamento é informação sensível).
 * Fica na tabela configuracao como hash; esqueceu o PIN: apagar a linha no Workbench
 * (DELETE FROM configuracao WHERE chave = 'relatorios.pin') e criar outro ao abrir.
 */
public class AcessoRelatoriosService {

    public static final String CHAVE_PIN = "relatorios.pin";

    private final ConfiguracaoDao configuracao;

    public AcessoRelatoriosService(ConfiguracaoDao configuracao) {
        this.configuracao = configuracao;
    }

    public boolean temPin() {
        return configuracao.get(CHAVE_PIN).isPresent();
    }

    public boolean confere(String pin) {
        return configuracao.get(CHAVE_PIN).map(guardado -> SenhaPin.confere(pin, guardado)).orElse(false);
    }

    public void definirPin(String pin) {
        configuracao.set(CHAVE_PIN, SenhaPin.gerarHash(pin));
    }
}
