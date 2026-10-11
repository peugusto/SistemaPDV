package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;
import com.casarural.sistemapdv.model.entities.Payment;
import com.casarural.sistemapdv.model.entities.SaleFilter;
import com.casarural.sistemapdv.model.entities.SaleSummary;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class OrderService {

    private OrderDao dao = DaoFactory.createOrderDAO();

    public void insert(Order obj) throws SQLException {
        if (obj.getItemPedido().isEmpty()) {
            throw new IllegalArgumentException("Não é possível salvar um pedido sem itens.");
        }
        dao.insert(obj);
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

    public void registerPartialPayment(Integer idCliente, BigDecimal valorPago, PaymentMethod metodo) {
        dao.registerPartialPayment(idCliente, valorPago, metodo);
    }

    public List<Order> findPaidOrdersByCustomer(Integer idCliente) {
        List<Order> orders = dao.findPaidOrdersByCustomer(idCliente);
        for (Order order : orders) {
            List<OrderItem> items = dao.findItemsByOrderId(order.getIdPedido());
            order.setItemPedido(items);
        }
        return orders;
    }
}