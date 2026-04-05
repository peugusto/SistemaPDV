package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;

import java.sql.SQLException;
import java.time.LocalDate;
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

    public List<OrderItem> findItemsByDate(LocalDate inicio, LocalDate fim) {
        return dao.findItemsByDate(inicio,fim);
    }

    public List<Order> findByCustomerPending(int idCliente) {
        return dao.findByCustomerPending(idCliente);
    }
}