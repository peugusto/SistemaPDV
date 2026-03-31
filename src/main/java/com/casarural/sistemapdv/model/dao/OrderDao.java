package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.Order;
import com.casarural.sistemapdv.model.entities.OrderItem;

import java.time.LocalDate;
import java.util.List;

public interface OrderDao extends GenericDao<Order> {
    List<OrderItem> findItemsByDate(LocalDate inicio, LocalDate fim);
}
