package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.dao.GenericDao;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public class ProductDaoJdbc implements GenericDao, ProductDao {

    private Connection conn = null;

    public ProductDaoJdbc (Connection conn) {
        this.conn = conn;
    }

    @Override
    public void insert(Object obj) {

    }

    @Override
    public Optional<Product> findById(Object id) {
        return Optional.empty();
    }

    @Override
    public List<Product> findAll() {
        return List.of();
    }

    @Override
    public void update(Object obj) {

    }

    @Override
    public void deleteById(Object id) {

    }
}
