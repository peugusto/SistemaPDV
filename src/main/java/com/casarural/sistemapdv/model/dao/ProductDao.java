package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Product;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public interface ProductDao extends GenericDao<Product> {
    Optional<Product> findByCodBarras(String codBarras);
}
