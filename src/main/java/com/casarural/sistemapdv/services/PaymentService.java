package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.PaymentDao;
import com.casarural.sistemapdv.model.entities.Payment;
import java.util.List;
import java.util.Optional;

public class PaymentService {

    private PaymentDao dao;

    public PaymentService(PaymentDao dao) {
        this.dao = dao;
    }


    public List<Payment> findAll() {
        return dao.findAll();
    }


    public Optional<Payment> findByOrder(Integer idPedido) {
        return dao.findById(idPedido);
    }
}