package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Product;

import java.util.Optional;

public interface ProductDao extends GenericDao<Product, Integer> {
    Optional<Product> findByCodBarras(String codBarras);
}
