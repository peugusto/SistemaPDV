package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CustomerDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;

import java.sql.SQLException;
import java.util.List;

public class CustomerService {
    private CustomerDao dao;

    public CustomerService(CustomerDao dao) {
        this.dao = dao;
    }

    public void insert(Customer obj) throws SQLException {
        obj.setSituacaoFiado(CustomerStatus.DISPONIVEL);
        dao.insert(obj);
    }

    public void saveOrUpdate(Customer obj) {
        if (obj.getIdCliente() == 0) {
            try {
                insert(obj);
            } catch (SQLException e) {
                throw new DbException(e.getMessage());
            }
        } else {
            double devendo = (obj.getTotalDevendo() != null) ? obj.getTotalDevendo() : 0.0;

            if (obj.getLimiteCredito() < devendo) {
                throw new DbException(String.format(
                        "O limite de crédito (R$ %.2f) não pode ser menor do que o total devendo atual (R$ %.2f)!",
                        obj.getLimiteCredito(), devendo
                ));
            }
            dao.update(obj);
        }
    }

    public void deleteById(Integer id){
        dao.deleteById(id);
    }


    public List<Customer> findAll() {
        return dao.findAll();
    }
}