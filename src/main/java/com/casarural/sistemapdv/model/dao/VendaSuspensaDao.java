package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.*;
import java.util.*;

public interface VendaSuspensaDao {
    List<VendaSuspensa> listar();
    VendaSuspensa gravar(VendaSuspensa.Gravacao gravacao);
    RetomadaVenda preparar(UUID id);
    VendaSuspensa retomar(RetomadaVenda preparacao,UUID operacao,UUID token,boolean pagamentoConferido);
    void descartar(VendaSuspensa venda,UUID operacao,boolean pagamentoConferido);
}
