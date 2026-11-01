package com.casarural.sistemapdv.model.dao;

import com.casarural.sistemapdv.model.entities.ExtratoFiado;
import com.casarural.sistemapdv.model.entities.FechamentoCaixa;
import com.casarural.sistemapdv.services.impressao.Comprovante;
import com.casarural.sistemapdv.model.entities.enums.PaymentMethod;
import java.time.LocalDateTime;

public interface RelatorioImpressaoDao {
    ExtratoFiado extratoFiado(int idCliente);
    FechamentoCaixa fechamentoCaixa(int idCaixa);
    default Comprovante vendaHistorica(int idVenda) { throw new UnsupportedOperationException("Segunda via de venda indisponível."); }
    default Comprovante recebimentoHistorico(int idCliente, Integer idRecebimento, LocalDateTime data, PaymentMethod forma) {
        throw new UnsupportedOperationException("Segunda via de recebimento indisponível.");
    }
}
