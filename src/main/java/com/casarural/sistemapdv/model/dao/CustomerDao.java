package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Customer;

public interface CustomerDao extends GenericDao<Customer>{
    /** Quantos pedidos (compras) estão ligados ao cliente. */
    int countOrders(Integer idCliente);
}
