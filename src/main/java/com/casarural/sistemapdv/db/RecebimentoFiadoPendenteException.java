package com.casarural.sistemapdv.db;

/** Há um pagamento confirmado que precisa ser conferido antes do próximo. */
public final class RecebimentoFiadoPendenteException extends DbException {
    private static final long serialVersionUID = 1L;

    public RecebimentoFiadoPendenteException(int idRecebimento) {
        super("O recebimento #" + idRecebimento + " já está registrado. Confira esse pagamento antes de receber outro.");
    }
}
