package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.dao.GenericDao;

import java.util.List;
import java.util.Optional;

public class CustomerDaoJdbc implements GenericDao {
    @Override
    public void insert(Object obj) {

    }

    @Override
    public Object findById(Object id) {
        return Optional.empty();
    }

    @Override
    public List findAll() {
        return List.of();
    }

    @Override
    public void update(Object obj) {

    }

    @Override
    public void deleteById(Object id) {

    }
}
