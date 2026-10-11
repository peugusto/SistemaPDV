package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.CashMovement;
import com.casarural.sistemapdv.model.entities.CashRegister;
import com.casarural.sistemapdv.model.entities.CashSummary;
import com.casarural.sistemapdv.model.entities.enums.CashMovementType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CashRegisterDao {
    /** O caixa aberto agora, se houver (o banco garante que existe no máximo um). */
    Optional<CashRegister> findOpen();

    CashRegister open(BigDecimal valorInicial);

    void close(int idCaixa, BigDecimal valorEsperado, BigDecimal valorContado, String observacao);

    void addMovement(int idCaixa, CashMovementType tipo, BigDecimal valor, String motivo);

    List<CashMovement> findMovements(int idCaixa);

    /** Totais de pagamentos e movimentos do caixa, prontos para as contas do resumo. */
    CashSummary summarize(CashRegister caixa);

    /** Caixas já fechados, do mais recente para o mais antigo. */
    List<CashRegister> findClosed(int limite);
}
