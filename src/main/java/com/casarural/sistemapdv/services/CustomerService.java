package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CustomerDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
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
            BigDecimal devendo = Money.of(obj.getTotalDevendo());

            if (obj.getLimiteCredito().compareTo(devendo) < 0) {
                throw new DbException(String.format(
                        "O limite de crédito (%s) não pode ser menor do que o total devendo atual (%s)!",
                        Money.format(obj.getLimiteCredito()), Money.format(devendo)
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