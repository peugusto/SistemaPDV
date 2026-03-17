package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.util.List;

public class ProductService {
    private ProductDao dao;

    public ProductService(ProductDao dao) {
        this.dao = dao;
    }

    public void saveOrUpdate(Product obj) {
        if (obj.getIdProduto() == 0) {
            dao.insert(obj);
        } else {
            dao.update(obj);
        }
    }

    public List<Product> findAll() {
        return dao.findAll();
    }
}
