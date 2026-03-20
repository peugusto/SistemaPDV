package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;
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
}