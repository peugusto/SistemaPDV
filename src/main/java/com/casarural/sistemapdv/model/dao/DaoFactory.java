package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.model.dao.impl.CustomerDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.ProductDaoJdbc;

public class DaoFactory {
    public static CustomerDao createCustomerDAO() {
        return new CustomerDaoJdbc(DB.getConnection());
    }

    public static ProductDao createProductDAO() {
        return new ProductDaoJdbc(DB.getConnection());
    }

}
