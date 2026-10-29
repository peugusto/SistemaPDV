package com.casarural.sistemapdv.db;

/** O servidor pode ter confirmado a operação, mas a resposta do commit não chegou. */
public class ResultadoTransacaoIncertoException extends DbException {

    private static final long serialVersionUID = 1L;

    public ResultadoTransacaoIncertoException(String operacao, Throwable causa) {
        super("Não foi possível confirmar o resultado de " + operacao + ". "
                + "A operação pode ter sido concluída. Não repita a operação nem devolva valores "
                + "antes de conferir o histórico de vendas, os recebimentos e os pagamentos.", causa);
    }
}
