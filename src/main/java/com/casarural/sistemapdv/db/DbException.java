package com.casarural.sistemapdv.db;

public class DbException extends RuntimeException{

    private static final long serialVersionUID = 1L;

    public DbException(String msg) {
        super(msg);
    }

    /**
     * Guarda a exceção original (ex.: SQLException) como causa. Assim o log mostra
     * o erro do MySQL e a linha exata do DAO onde ele aconteceu, não só a mensagem.
     */
    public DbException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
