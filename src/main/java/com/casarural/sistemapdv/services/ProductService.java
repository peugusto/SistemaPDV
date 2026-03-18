package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.util.List;
import java.util.Optional;

public class ProductService {
    private ProductDao dao;

    public ProductService(ProductDao dao) {
        this.dao = dao;
    }

    public void delete(Integer id){
        dao.deleteById(id);
    }

    public Optional<Product> findByCodBarras(String codBarras) {
        return dao.findByCodBarras(codBarras);
    }

    public void saveOrUpdate(Product obj) {
        if (obj.getIdProduto() > 0) {
            dao.update(obj);
        } else {
            dao.insert(obj);
        }
    }

    public List<Product> findAll() {
        return dao.findAll();
    }
}
