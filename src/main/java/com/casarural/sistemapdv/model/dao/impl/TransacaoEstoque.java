package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.*;
import java.sql.*;
import java.util.function.Supplier;

final class TransacaoEstoque {
    @FunctionalInterface interface Operacao<T>{T executar(Connection c)throws SQLException;}
    private TransacaoEstoque(){}
    static <T>T executar(Supplier<Connection> conexoes,String descricao,Operacao<T> operacao){
        Connection c=null;boolean iniciou=false,encerrou=false,commit=false;
        try{
            c=conexoes.get();c.setAutoCommit(false);iniciou=true;T resultado=operacao.executar(c);commit=true;c.commit();encerrou=true;return resultado;
        }catch(SQLException|RuntimeException e){
            if(iniciou)try{c.rollback();encerrou=true;}catch(SQLException|RuntimeException rollback){e.addSuppressed(rollback);}
            if(commit||iniciou&&!encerrou)throw new ResultadoTransacaoIncertoException(descricao,e);
            if(e instanceof RuntimeException)throw (RuntimeException)e;throw new DbException("Não foi possível "+descricao+". Tente novamente.",e);
        }finally{
            if(c!=null){
                try{if(encerrou)c.setAutoCommit(true);else c.abort(Runnable::run);}catch(SQLException|RuntimeException e){log(e);}
                try{c.close();}catch(SQLException|RuntimeException e){log(e);}
            }
        }
    }
    private static void log(Exception e){org.slf4j.LoggerFactory.getLogger(TransacaoEstoque.class).warn("Falha na liberação da conexão de estoque",e);}
}
