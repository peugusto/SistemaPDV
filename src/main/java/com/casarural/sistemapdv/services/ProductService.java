package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ProductDao;
import com.casarural.sistemapdv.model.entities.Product;

import java.sql.SQLException;
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

    public void saveOrUpdate(Product obj) throws SQLException {
        Optional<Product> mesmoCodigo = dao.findByCodBarras(obj.getCodBarras());
        if (mesmoCodigo.isPresent() && mesmoCodigo.get().getIdProduto() != obj.getIdProduto()) {
            throw new DbException(String.format("O código de barras '%s' já está cadastrado no produto '%s'.",
                    obj.getCodBarras(), mesmoCodigo.get().getNomeProduto()));
        }

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
