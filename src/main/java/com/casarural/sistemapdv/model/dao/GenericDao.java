package com.casarural.sistemapdv.model.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface GenericDao<T, Integer> {
    void insert(T obj) throws SQLException;
    Optional<T> findById(Integer id);
    List<T> findAll();
    void update(T obj);
    void deleteById(Integer id);
}
