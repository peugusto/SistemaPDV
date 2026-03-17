package com.casarural.sistemapdv.model.dao;

import java.util.List;

public interface GenericDao<T, K> {
    void insert(T obj);
    T findById(K id);
    List<T> findAll();
    void update(T obj);
    void deleteById(K id);
}
