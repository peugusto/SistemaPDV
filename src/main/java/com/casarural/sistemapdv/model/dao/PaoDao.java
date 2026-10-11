package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.DiaPao;
import com.casarural.sistemapdv.model.entities.enums.TurnoPao;

import java.time.LocalDate;
import java.util.List;

public interface PaoDao {

    /** Compra e venda do pão em cada dia de [de, ate], um DiaPao por dia (dias sem nada também). */
    List<DiaPao> dias(int idProduto, LocalDate de, LocalDate ate);

    /** Quantos pães chegaram no turno. Registrar de novo o mesmo dia e turno corrige a quantidade. */
    void registrarCompra(int idProduto, LocalDate data, TurnoPao turno, int quantidade);
}
