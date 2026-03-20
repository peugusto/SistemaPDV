package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.entities.Order;

import java.util.List;
import java.util.Optional;

public class PaymentDaoJdbc implements OrderDao {
    @Override
    public void insert(Order obj) {

    }

    @Override
    public Optional<Order> findById(Integer id) {
        return Optional.empty();
    }

    @Override
    public List<Order> findAll() {
        return List.of();
    }

    @Override
    public void update(Order obj) {

    }

    @Override
    public void deleteById(Integer id) {

    }
}
