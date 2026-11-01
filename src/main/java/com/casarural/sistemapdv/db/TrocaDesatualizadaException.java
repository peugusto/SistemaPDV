package com.casarural.sistemapdv.db;

/** Recusa antes de qualquer escrita: a troca precisa ser aberta novamente. */
public class TrocaDesatualizadaException extends DbException {
    public TrocaDesatualizadaException(int idPedido) {
        super("A venda #"+idPedido+" mudou desde a abertura da troca. Nenhuma alteração foi feita. "
                +"Reabra a troca pelo histórico para usar os pagamentos e créditos atualizados.");
    }
}
