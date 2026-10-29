package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.ExtratoFiado;
import com.casarural.sistemapdv.model.entities.FechamentoCaixa;

public interface RelatorioImpressaoDao {
    ExtratoFiado extratoFiado(int idCliente);
    FechamentoCaixa fechamentoCaixa(int idCaixa);
}
