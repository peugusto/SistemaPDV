package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.dao.impl.CashRegisterDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.CustomerDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.OrderDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.ProductDaoJdbc;

public class DaoFactory {
    public static CustomerDao createCustomerDAO() { return new CustomerDaoJdbc(); }
    public static ProductDao createProductDAO() { return new ProductDaoJdbc(); }
    public static OrderDao createOrderDAO() { return new OrderDaoJdbc(); }
    public static CashRegisterDao createCashRegisterDAO() { return new CashRegisterDaoJdbc(); }
}
