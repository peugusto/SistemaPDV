package com.casarural.sistemapdv.model.dao;
import com.casarural.sistemapdv.model.entities.*;
import java.util.List;

public interface AjusteEstoqueDao {
    RegistroAjusteEstoque registrar(AjusteEstoque ajuste);
    List<RegistroAjusteEstoque> historico(int produto,int limite);
}
