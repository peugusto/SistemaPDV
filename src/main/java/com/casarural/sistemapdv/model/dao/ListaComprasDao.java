package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.RegraListaCompras.Item;
import java.util.List;

public interface ListaComprasDao {
    List<Item> buscarPendencias();
}
