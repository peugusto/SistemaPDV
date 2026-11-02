package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.dao.impl.CashRegisterDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.ConfiguracaoDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.CustomerDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.OrderDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.PaoDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.ProductDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.RelatorioDaoJdbc;
import com.casarural.sistemapdv.model.dao.impl.RelatorioImpressaoDaoJdbc;

public class DaoFactory {
    public static VendaSuspensaDao createVendaSuspensaDAO() { return proteger(VendaSuspensaDao.class,new com.casarural.sistemapdv.model.dao.impl.VendaSuspensaDaoJdbc()); }
    private static <T>T proteger(Class<T> tipo,T dao) { return com.casarural.sistemapdv.util.BancoEmSegundoPlano.proteger(tipo,dao); }
    public static ListaComprasDao createListaComprasDAO() { return proteger(ListaComprasDao.class,new com.casarural.sistemapdv.model.dao.impl.ListaComprasDaoJdbc()); }
    public static CustomerDao createCustomerDAO() { return proteger(CustomerDao.class,new CustomerDaoJdbc()); }
    public static ProductDao createProductDAO() { return proteger(ProductDao.class,new ProductDaoJdbc()); }
    public static OrderDao createOrderDAO() { return proteger(OrderDao.class,new OrderDaoJdbc()); }
    public static CashRegisterDao createCashRegisterDAO() { return proteger(CashRegisterDao.class,new CashRegisterDaoJdbc()); }
    public static RelatorioImpressaoDao createRelatorioImpressaoDAO() { return proteger(RelatorioImpressaoDao.class,new RelatorioImpressaoDaoJdbc()); }
    public static RelatorioDao createRelatorioDAO() { return proteger(RelatorioDao.class,new RelatorioDaoJdbc()); }
    public static PaoDao createPaoDAO() { return proteger(PaoDao.class,new PaoDaoJdbc()); }
    public static ConfiguracaoDao createConfiguracaoDAO() { return proteger(ConfiguracaoDao.class,new ConfiguracaoDaoJdbc()); }
    public static ArquivoComprovanteDao createArquivoComprovanteDAO() { return proteger(ArquivoComprovanteDao.class,new com.casarural.sistemapdv.model.dao.impl.ArquivoComprovanteDaoJdbc()); }
}
