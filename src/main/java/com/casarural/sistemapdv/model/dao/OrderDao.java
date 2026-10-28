package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.model.entities.SaleFilter;
import com.casarural.sistemapdv.model.entities.SaleSummary;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrderDao extends GenericDao<Order> {
    List<SaleSummary> findSales(SaleFilter filtro, int limite);
    List<Payment> findPaymentsByOrder(int idPedido);
    BigDecimal getCustomerDebt(Integer idCliente);
    RecebimentoFiado registerPartialPayment(Integer idCliente, BigDecimal valorPago, PaymentMethod metodo);
    List<Order> findOpenFiadoOrders(Integer idCliente);
    List<Payment> findFiadoReceipts(Integer idCliente);
    List<Order> findPaidOrdersByCustomer(Integer idCliente);
    List<OrderItem> findItemsByOrderId(int idPedido);

    /** Venda com itens (produto completo) e quanto foi pago em cada forma, para cancelar ou trocar. */
    Optional<VendaParaDesfazer> findParaDesfazer(int idPedido);

    /**
     * Cancela a venda (só do caixa aberto): estoque volta, pagamentos vão para pagamento_estornado.
     * Devolve quanto o cliente tinha pago em cada forma (dinheiro, PIX, cartão) para devolver.
     */
    Map<PaymentMethod, BigDecimal> cancel(int idPedido, String motivo);

    /**
     * Desfaz um recebimento de fiado (só do caixa aberto): as compras que ele tinha quitado voltam a dever.
     * Devolve o valor estornado.
     */
    BigDecimal reverseFiadoReceipt(int idCliente, LocalDateTime dataPagamento, PaymentMethod metodo, String motivo);
}
