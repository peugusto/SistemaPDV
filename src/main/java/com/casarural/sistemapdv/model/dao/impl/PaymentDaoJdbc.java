package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.dao.OrderDao;
import com.casarural.sistemapdv.model.dao.PaymentDao;
import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.Payment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class PaymentDaoJdbc implements PaymentDao {

    @Override
    public void insert(Payment obj) {

    }

    @Override
    public Optional<Payment> findById(Integer id) {
        return Optional.empty();
    }

    @Override
    public List<Payment> findAll() {
        return List.of();
    }


    @Override
    public void update(Payment obj) {

    }

    @Override
    public void deleteById(Integer id) {

    }

    @Override
    public Payment instantiate(ResultSet rs) throws SQLException {
        return null;
    }
}
