package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.SaleFilter;
import com.casarural.sistemapdv.model.entities.SaleSummary;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

public interface OrderDao extends GenericDao<Order> {
    List<SaleSummary> findSales(SaleFilter filtro, int limite);
    List<Payment> findPaymentsByOrder(int idPedido);
    BigDecimal getCustomerDebt(Integer idCliente);
    void registerPartialPayment(Integer idCliente, BigDecimal valorPago, PaymentMethod metodo);
    List<Order> findOpenFiadoOrders(Integer idCliente);
    List<Payment> findFiadoReceipts(Integer idCliente);
    List<Order> findPaidOrdersByCustomer(Integer idCliente);
    List<OrderItem> findItemsByOrderId(int idPedido);
}
