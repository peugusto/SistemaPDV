package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.model.dao.*;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.VendaSuspensa.*;
import java.util.*;

public class VendaSuspensaService {
    private final VendaSuspensaDao dao;
    public VendaSuspensaService(){this(DaoFactory.createVendaSuspensaDAO());}
    public VendaSuspensaService(VendaSuspensaDao dao){this.dao=dao;}
    public List<VendaSuspensa> listar(){return dao.listar();}
    public VendaSuspensa gravar(Gravacao g){return dao.gravar(g);}
    public RetomadaVenda preparar(UUID id){return dao.preparar(id);}
    public VendaSuspensa retomar(RetomadaVenda p,UUID operacao,UUID token,boolean conferido){return dao.retomar(p,operacao,token,conferido);}
    public void descartar(VendaSuspensa v,UUID operacao,boolean conferido){dao.descartar(v,operacao,conferido);}
    public Gravacao gravacao(VendaSuspensa atual,String nome,List<OrderItem> itens,Estado destino){
        return new Gravacao(atual==null?UUID.randomUUID():atual.id,UUID.randomUUID(),atual==null?0:atual.versao,
                atual==null?null:atual.token,nome,destino,VendaSuspensa.capturar(itens));
    }
    public Gravacao interromperPagamento(VendaSuspensa atual){return new Gravacao(atual.id,UUID.randomUUID(),atual.versao,atual.token,atual.identificacao,Estado.EM_PAGAMENTO,atual.itens,true);}
}
