package com.casarural.sistemapdv.db;

import java.sql.*;
import java.util.UUID;
import java.util.function.Supplier;

/** Um lock por processo, sem consultas periódicas. O MySQL o libera quando a conexão morre. */
public final class SessaoVendasSuspensas implements AutoCloseable {
    public static final String PREFIXO="pdv_carrinho_";
    private static final SessaoVendasSuspensas ATUAL=new SessaoVendasSuspensas(DB::getConnection);
    private final Supplier<Connection> conexoes;
    private final String id=UUID.randomUUID().toString();
    private Connection conexao;
    public SessaoVendasSuspensas(Supplier<Connection> conexoes){this.conexoes=conexoes;}
    public static SessaoVendasSuspensas atual(){return ATUAL;}
    public String id(){return id;}
    public synchronized void garantir() {
        try {
            if(conexao!=null)try(PreparedStatement st=conexao.prepareStatement("SELECT IS_USED_LOCK(?) = CONNECTION_ID()")){
                st.setString(1,PREFIXO+id);try(ResultSet rs=st.executeQuery()){if(rs.next()&&rs.getBoolean(1))return;}
            }
        }catch(SQLException|RuntimeException e){descartarConexao();}
        descartarConexao();
        try {
            conexao=conexoes.get();
            try(PreparedStatement st=conexao.prepareStatement("SELECT GET_LOCK(?, 0)")){
                st.setString(1,PREFIXO+id);try(ResultSet rs=st.executeQuery()){if(!rs.next()||rs.getInt(1)!=1)throw new SQLException("Sessão indisponível.");}
            }
        }catch(SQLException|RuntimeException e){descartarConexao();throw new DbException("Não foi possível proteger a retomada do carrinho. Tente novamente.",e);}
    }
    private void descartarConexao(){
        if(conexao==null)return;
        try{conexao.abort(Runnable::run);}catch(SQLException|RuntimeException e){/* conexão não volta ao pool com lock desconhecido */}
        try{conexao.close();}catch(SQLException|RuntimeException e){/* descartada */}
        conexao=null;
    }
    @Override public synchronized void close(){
        if(conexao==null)return;
        try(PreparedStatement st=conexao.prepareStatement("SELECT RELEASE_LOCK(?)")){
            st.setString(1,PREFIXO+id);st.executeQuery().close();
        }catch(SQLException|RuntimeException e){descartarConexao();return;}
        try{conexao.close();}catch(SQLException|RuntimeException e){/* lock já liberado */}finally{conexao=null;}
    }
}
