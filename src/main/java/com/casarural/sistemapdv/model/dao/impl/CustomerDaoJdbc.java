package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.dao.CustomerDao;
import com.casarural.sistemapdv.model.dao.GenericDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.Product;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public class CustomerDaoJdbc implements GenericDao, CustomerDao {

    private Connection conn = null;

    public CustomerDaoJdbc (Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insert(Object obj) {

    }

    @Override
    public Optional<Customer> findById(Object id) {
        return Optional.empty();
    }

    @Override
    public List<Customer> findAll() {
        return List.of();
    }

    @Override
    public void update(Object obj) {

    }

    @Override
    public void deleteById(Object id) {

    }
}
