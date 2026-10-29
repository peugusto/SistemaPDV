package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.RecebimentoFiado;
import com.casarural.sistemapdv.model.entities.SaleFilter;
import com.casarural.sistemapdv.model.entities.SaleSummary;
import com.casarural.sistemapdv.model.entities.VendaParaDesfazer;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OrderService {

    private OrderDao dao = DaoFactory.createOrderDAO();

    public void insert(Order obj) throws SQLException {
        if (obj == null || obj.getItemPedido() == null || obj.getItemPedido().isEmpty()) {
            throw new IllegalArgumentException("Não é possível salvar um pedido sem itens.");
        }

        validarVenda(obj);
        // Confere a venda completa, incluindo créditos herdados de uma troca.
        // A recusa acontece antes do DAO, que cancela a venda original ao gravar a troca.
        BigDecimal totalFiado = obj.getTotalPagamentos(PaymentMethod.FIADO);
        if (totalFiado.compareTo(obj.getTotalPermitidoNoFiado()) > 0) {
            throw new IllegalArgumentException(
                    "O fiado, incluindo créditos da troca, está cobrindo produtos "
                            + "que precisam ser pagos à vista. Volte ao carrinho e revise a venda.");
        }

        dao.insert(obj);
    }

    private static void validarVenda(Order obj) {
        BigDecimal maximo = new BigDecimal("99999999.99");
        for (OrderItem item : obj.getItemPedido()) {
            if (item == null || item.getQtd() <= 0 || item.getPrecoUnitario().signum() < 0
                    || item.getPrecoUnitario().compareTo(maximo) > 0)
                throw new IllegalArgumentException("Revise a quantidade e o preço dos itens antes de salvar.");
        }
        if (obj.getValorTotal().signum() < 0 || obj.getValorTotal().compareTo(maximo) > 0
                || obj.getDesconto().signum() < 0 || obj.getDesconto().compareTo(obj.getSubtotalItens()) > 0)
            throw new IllegalArgumentException("O total ou desconto da venda está fora do limite permitido.");
    }
    public List<Order> findAll() {
        return dao.findAll();
    }

    public Optional<Order> findById(Integer id) {
        return dao.findById(id);
    }

    public List<SaleSummary> findSales(SaleFilter filtro, int limite) {
        return dao.findSales(filtro, limite);
    }

    public List<Payment> findPaymentsByOrder(int idPedido) {
        return dao.findPaymentsByOrder(idPedido);
    }

    public List<Order> findOpenFiadoOrders(int idCliente) {
        return dao.findOpenFiadoOrders(idCliente);
    }

    public List<Payment> findFiadoReceipts(int idCliente) {
        return dao.findFiadoReceipts(idCliente);
    }

    public List<OrderItem> findItemsByOrderId(int idPedido) {
        return dao.findItemsByOrderId(idPedido);
    }

    public BigDecimal getCustomerDebt(Integer idCliente) {
        return dao.getCustomerDebt(idCliente);
    }

    public RecebimentoFiado registerPartialPayment(Integer idCliente, BigDecimal valorPago, PaymentMethod metodo) {
        return dao.registerPartialPayment(idCliente, valorPago, metodo);
    }

    public List<Order> findPaidOrdersByCustomer(Integer idCliente) {
        List<Order> orders = dao.findPaidOrdersByCustomer(idCliente);
        for (Order order : orders) {
            List<OrderItem> items = dao.findItemsByOrderId(order.getIdPedido());
            order.setItemPedido(items);
        }
        return orders;
    }

    public Optional<VendaParaDesfazer> findParaDesfazer(int idPedido) {
        return dao.findParaDesfazer(idPedido);
    }

    /** Cancela a venda. Devolve o que o cliente tinha pago em cada forma (para devolver a ele). */
    public Map<PaymentMethod, BigDecimal> cancelar(int idPedido, String motivo) {
        return dao.cancel(idPedido, motivoObrigatorio(motivo));
    }

    public BigDecimal estornarRecebimento(int idCliente, LocalDateTime dataPagamento, PaymentMethod metodo, String motivo) {
        return dao.reverseFiadoReceipt(idCliente, dataPagamento, metodo, motivoObrigatorio(motivo));
    }

    private static String motivoObrigatorio(String motivo) {
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Escreva o motivo.");
        String m = motivo.trim();
        return m.length() > 255 ? m.substring(0, 255) : m;
    }
}
