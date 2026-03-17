package com.casarural.sistemapdv.model.dao;

import java.util.List;
import java.util.Optional;

public interface GenericDao<T, K> {
    void insert(T obj);
    Optional<T> findById(K id);
    List<T> findAll();
    void update(T obj);
    void deleteById(K id);
}
