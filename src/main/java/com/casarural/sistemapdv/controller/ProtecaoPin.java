package com.casarural.sistemapdv.controller;

import com.casarural.sistemapdv.model.dao.DaoFactory;
import com.casarural.sistemapdv.services.AcessoRelatoriosService;
import javafx.stage.Window;

/** Autorização pontual pelo PIN existente; não mantém sessão de administrador. */
public class ProtecaoPin {
    private final AcessoRelatoriosService acesso;
    public ProtecaoPin() { this(new AcessoRelatoriosService(DaoFactory.createConfiguracaoDAO())); }
    ProtecaoPin(AcessoRelatoriosService acesso) { this.acesso=acesso; }
    public boolean autorizar(Window dono,String acao) {
        return PinDialogo.liberar(acesso,dono,"Autorização necessária",acao);
    }
}
