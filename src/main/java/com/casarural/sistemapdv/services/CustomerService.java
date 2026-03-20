package com.casarural.sistemapdv.services;

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

    public void deleteById(Integer id){
        dao.deleteById(id);
    }


    public List<Customer> findAll() {
        return dao.findAll();
    }
}