package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.ListaComprasDao;
import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import java.util.List;

public class ListaComprasService {
    private final ListaComprasDao dao;
    public ListaComprasService(ListaComprasDao dao) { this.dao = dao; }
    public List<Item> buscarPendencias() { return dao.buscarPendencias(); }
}
