package com.casarural.sistemapdv.services;
import com.casarural.sistemapdv.model.dao.*;
import com.casarural.sistemapdv.model.entities.*;
import java.util.*;

public class AjusteEstoqueService {
    private final AjusteEstoqueDao dao;
    public AjusteEstoqueService(){this(DaoFactory.createAjusteEstoqueDAO());}
    public AjusteEstoqueService(AjusteEstoqueDao dao){this.dao=dao;}
    public RegistroAjusteEstoque registrar(AjusteEstoque a){return dao.registrar(Objects.requireNonNull(a,"Ajuste não informado."));}
    public List<RegistroAjusteEstoque> historico(int produto){return dao.historico(produto,200);}
}
