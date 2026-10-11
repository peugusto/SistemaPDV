package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.PontoVenda;
import com.casarural.sistemapdv.model.entities.ResumoVendas;
import com.casarural.sistemapdv.model.entities.VendaProduto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Consultas dos dashboards. Períodos sempre [de, ate): inclui "de", não inclui "ate". */
public interface RelatorioDao {

    ResumoVendas resumo(LocalDateTime de, LocalDateTime ate);

    /** Só os dias que tiveram venda; quem chama completa os dias vazios. */
    Map<LocalDate, PontoVenda> vendasPorDia(LocalDateTime de, LocalDateTime ate);

    /** Chave = hora do dia (0 a 23); só as horas que tiveram venda. */
    Map<Integer, PontoVenda> vendasPorHora(LocalDateTime de, LocalDateTime ate);

    /** Todos os produtos cadastrados, inclusive os que não venderam nada no período. */
    List<VendaProduto> vendasPorProduto(LocalDateTime de, LocalDateTime ate);
}
