package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CashRegisterDao;
import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.entities.CashMovement;
import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.model.entities.CashSummary;
import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Regras do caixa: abrir com o troco, registrar sangrias e reforços, calcular o resumo e fechar.
 * As contas ficam em CashSummary (testadas); aqui ficam as validações e o registro em log.
 */
public class CashRegisterService {

    private static final Logger log = LoggerFactory.getLogger(CashRegisterService.class);

    private final CashRegisterDao dao;

    public CashRegisterService() {
        this(DaoFactory.createCashRegisterDAO());
    }

    public CashRegisterService(CashRegisterDao dao) {
        this.dao = dao;
    }

    public Optional<CashRegister> findOpen() {
        return dao.findOpen();
    }

    /** Caixa aberto num dia anterior (esqueceram de fechar). */
    public Optional<CashRegister> findOpenFromPreviousDay() {
        return dao.findOpen().filter(c -> c.getAbertoEm().toLocalDate().isBefore(LocalDate.now()));
    }

    public CashRegister open(BigDecimal valorInicial) {
        if (valorInicial == null || valorInicial.signum() < 0) {
            throw new DbException("Informe o dinheiro que está na gaveta (pode ser 0,00).");
        }
        CashRegister caixa = dao.open(Money.of(valorInicial));
        log.info("Caixa {} aberto com troco de {}", caixa.getIdCaixa(), Money.format(caixa.getValorInicial()));
        return caixa;
    }

    public CashSummary summarize(CashRegister caixa) {
        return dao.summarize(caixa);
    }

    public List<CashMovement> findMovements(CashRegister caixa) {
        return dao.findMovements(caixa.getIdCaixa());
    }

    /** Retirada de dinheiro da gaveta. Não deixa tirar mais do que o sistema calcula que há nela. */
    public void addSangria(CashRegister caixa, BigDecimal valor, String motivo) {
        validarMovimento(caixa, valor);
        BigDecimal naGaveta = dao.summarize(caixa).getDinheiroEsperado();
        if (valor.compareTo(naGaveta) > 0) {
            throw new DbException(String.format(
                    "Pela conta do sistema há %s em dinheiro na gaveta. Não é possível retirar %s.",
                    Money.format(naGaveta), Money.format(valor)));
        }
        dao.addMovement(caixa.getIdCaixa(), CashMovementType.SANGRIA, Money.of(valor), limpar(motivo));
        log.info("Caixa {}: sangria de {} ({})", caixa.getIdCaixa(), Money.format(valor), limpar(motivo));
    }

    /** Entrada de dinheiro na gaveta que não é venda (ex.: mais troco). */
    public void addReforco(CashRegister caixa, BigDecimal valor, String motivo) {
        validarMovimento(caixa, valor);
        dao.addMovement(caixa.getIdCaixa(), CashMovementType.REFORCO, Money.of(valor), limpar(motivo));
        log.info("Caixa {}: reforço de {} ({})", caixa.getIdCaixa(), Money.format(valor), limpar(motivo));
    }

    /**
     * Fecha o caixa. O esperado é calculado agora; valorContado é opcional
     * (null = fechado sem conferência, sem inventar diferença zero).
     */
    public CashRegister close(CashRegister caixa, BigDecimal valorContado, String observacao) {
        if (valorContado != null && valorContado.signum() < 0) {
            throw new DbException("O valor contado não pode ser negativo.");
        }
        CashSummary resumo = dao.summarize(caixa);
        BigDecimal esperado = resumo.getDinheiroEsperado();
        dao.close(caixa.getIdCaixa(), esperado, valorContado == null ? null : Money.of(valorContado), limpar(observacao));

        caixa.setValorEsperado(esperado);
        caixa.setValorContado(valorContado);
        if (valorContado == null) {
            log.info("Caixa {} fechado sem conferência. Esperado em dinheiro: {}", caixa.getIdCaixa(), Money.format(esperado));
        } else if (caixa.getDiferenca().signum() == 0) {
            log.info("Caixa {} fechado. Dinheiro conferido: {} (bateu)", caixa.getIdCaixa(), Money.format(valorContado));
        } else {
            log.warn("Caixa {} fechado com diferença de {}. Esperado {}, contado {}", caixa.getIdCaixa(),
                    Money.format(caixa.getDiferenca()), Money.format(esperado), Money.format(valorContado));
        }
        return caixa;
    }

    public List<CashRegister> findClosed(int limite) {
        return dao.findClosed(limite);
    }

    private static void validarMovimento(CashRegister caixa, BigDecimal valor) {
        if (caixa == null || !caixa.isAberto()) {
            throw new DbException("O caixa está fechado.");
        }
        if (!Money.isPositive(valor)) {
            throw new DbException("Informe um valor maior que zero.");
        }
    }

    private static String limpar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        return t.length() > 150 ? t.substring(0, 150) : t;
    }
}
