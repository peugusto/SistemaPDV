package com.casarural.sistemapdv.model.entities;

import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Regra do bloqueio automático do fiado (só contas, sem banco e sem tela; ver RegraFiadoTest).
 *
 * Uma compra no fiado tem PRAZO_DIAS para ser paga. Se alguma compra passar do prazo com
 * valor ainda em aberto, a conta do cliente fica bloqueada para novas compras no fiado.
 * Ela se desbloqueia sozinha quando essas compras atrasadas forem pagas: como os pagamentos
 * de fiado abatem sempre a compra mais antiga primeiro, basta pagar o "valor em atraso".
 *
 * O bloqueio não é gravado no banco: é calculado a cada consulta. Por isso não existe como
 * desbloquear manualmente, e não é preciso nenhuma rotina agendada para bloquear.
 */
public final class RegraFiado {

    /** Prazo para pagar uma compra no fiado. */
    public static final int PRAZO_DIAS = 30;

    private RegraFiado() {}

    /** Compras feitas antes deste momento já passaram do prazo. */
    public static LocalDateTime limite(LocalDateTime agora) {
        return agora.minusDays(PRAZO_DIAS);
    }

    /** Dia em que a compra vence (data da compra + prazo). */
    public static LocalDate vencimento(Order compra) {
        return compra.getDataPedido().toLocalDate().plusDays(PRAZO_DIAS);
    }

    /** A compra passou do prazo e ainda tem valor em aberto. */
    public static boolean estaAtrasada(Order compra, LocalDateTime agora) {
        return Money.isPositive(compra.getFalta()) && compra.getDataPedido().isBefore(limite(agora));
    }

    /** Quanto o cliente precisa pagar para a conta desbloquear (zero = não está em atraso). */
    public static BigDecimal valorEmAtraso(List<Order> comprasEmAberto, LocalDateTime agora) {
        return comprasEmAberto.stream()
                .filter(c -> estaAtrasada(c, agora))
                .map(Order::getFalta)
                .reduce(Money.ZERO, BigDecimal::add);
    }
}
