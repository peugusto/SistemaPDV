package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.util.List;

public class ProductService {
    private ProductDao dao;

    public ProductService(ProductDao dao) {
        this.dao = dao;
    }

    public void insert(Product obj){
        dao.insert(obj);
    }

    public void delete(Integer id){
        dao.deleteById(id);
    }

    public List<Product> findAll() {
        return dao.findAll();
    }
}
