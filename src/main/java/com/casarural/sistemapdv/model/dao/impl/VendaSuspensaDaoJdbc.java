package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.dao.VendaSuspensaDao;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.VendaSuspensa.*;
import com.casarural.sistemapdv.model.entities.enums.TipoVenda;
import java.math.BigDecimal;
import java.io.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

public class VendaSuspensaDaoJdbc implements VendaSuspensaDao {
    private final Supplier<Connection> conexoes;
    private final SessaoVendasSuspensas sessao;
    public VendaSuspensaDaoJdbc(){this(DB::getConnection,SessaoVendasSuspensas.atual());}
    public VendaSuspensaDaoJdbc(Supplier<Connection> conexoes,SessaoVendasSuspensas sessao){this.conexoes=conexoes;this.sessao=sessao;}
    @FunctionalInterface private interface Operacao<T>{T executar(Connection c)throws SQLException;}
    private <T>T transacao(Operacao<T> op){
        sessao.garantir();Connection c=null;boolean iniciou=false,encerrou=false,commit=false;
        try {
            c=conexoes.get();c.setAutoCommit(false);iniciou=true;T r=op.executar(c);commit=true;c.commit();encerrou=true;return r;
        }catch(SQLException|RuntimeException e){
            if(iniciou)try{c.rollback();encerrou=true;}catch(SQLException|RuntimeException r){e.addSuppressed(r);}
            if(commit||iniciou&&!encerrou)throw new ResultadoTransacaoIncertoException("salvar o carrinho",e);
            if(e instanceof RuntimeException)throw (RuntimeException)e;throw new DbException(e.getMessage(),e);
        }finally{
            if(c!=null){
                try{if(encerrou)c.setAutoCommit(true);else c.abort(Runnable::run);}catch(SQLException|RuntimeException e){logLimpeza(e);}
                try{c.close();}catch(SQLException|RuntimeException e){logLimpeza(e);}
            }
        }
    }
    private static void logLimpeza(Exception e){org.slf4j.LoggerFactory.getLogger(VendaSuspensaDaoJdbc.class).warn("Falha na liberação da conexão do carrinho",e);}
    private static VendaSuspensaIndisponivelException indisponivel(){return new VendaSuspensaIndisponivelException("Este carrinho mudou ou está em uso em outra tela. Atualize a lista; nenhuma venda foi registrada por esta tentativa.");}
    private VendaSuspensa ler(Connection c,UUID id,boolean lock)throws SQLException{
        try(PreparedStatement st=c.prepareStatement("SELECT * FROM venda_suspensa WHERE id=?"+(lock?" FOR UPDATE":""))){
            st.setString(1,id.toString());try(ResultSet rs=st.executeQuery()){
                if(!rs.next())throw indisponivel();
                String nome=rs.getString("identificacao"),dono=rs.getString("sessao"),token=rs.getString("token");
                long versao=rs.getLong("versao");Estado estado=Estado.valueOf(rs.getString("estado"));
                java.time.LocalDateTime criada=rs.getTimestamp("criada_em").toLocalDateTime(),atual=rs.getTimestamp("atualizada_em").toLocalDateTime();
                Number pedido=(Number)rs.getObject("id_pedido");List<Linha> itens=new ArrayList<>();
                try(PreparedStatement i=c.prepareStatement("SELECT * FROM venda_suspensa_item WHERE venda_id=? ORDER BY linha")){
                    i.setString(1,id.toString());try(ResultSet r=i.executeQuery()){while(r.next())itens.add(new Linha(r.getInt("id_produto"),r.getString("nome_produto"),r.getString("cod_barras"),r.getInt("quantidade"),r.getBigDecimal("preco_unitario"),TipoVenda.valueOf(r.getString("tipo_venda")),r.getBigDecimal("preco_cadastro")));}
                }
                return new VendaSuspensa(id,nome,versao,estado,dono,token,criada,atual,pedido==null?null:pedido.intValue(),itens);
            }
        }
    }
    private boolean viva(Connection c,String dono)throws SQLException{
        if(dono==null)return false;
        try(PreparedStatement st=c.prepareStatement("SELECT IS_USED_LOCK(?)")){st.setString(1,SessaoVendasSuspensas.PREFIXO+dono);try(ResultSet rs=st.executeQuery()){return rs.next()&&rs.getObject(1)!=null;}}
    }
    private void disponivel(Connection c,VendaSuspensa v,boolean pagamentoConferido)throws SQLException{
        if(v.estado!=Estado.SUSPENSA&&v.estado!=Estado.EM_USO&&v.estado!=Estado.EM_PAGAMENTO || v.estado!=Estado.SUSPENSA&&viva(c,v.sessao))throw indisponivel();
        if(v.estado==Estado.EM_PAGAMENTO&&!pagamentoConferido)throw new VendaSuspensaIndisponivelException("O pagamento deste carrinho foi interrompido. Confira o histórico e os valores recebidos antes de retomá-lo.");
    }
    private void meu(VendaSuspensa v,long versao,String token){
        if(v.versao!=versao||!Objects.equals(sessao.id(),v.sessao)||!Objects.equals(token,v.token))throw indisponivel();
    }
    private boolean repetida(Connection c,UUID id,UUID operacao,String assinatura)throws SQLException{
        try(PreparedStatement st=c.prepareStatement("SELECT ultima_operacao, assinatura FROM venda_suspensa WHERE id=?")){
            st.setString(1,id.toString());try(ResultSet r=st.executeQuery()){
                if(!r.next()||!operacao.toString().equals(r.getString(1)))return false;
                if(!assinatura.equals(r.getString(2)))throw new DbException("A mesma tentativa não pode ser repetida com outro conteúdo.");return true;
            }
        }
    }
    private static String hash(Object...valores){
        try{
            MessageDigest d=MessageDigest.getInstance("SHA-256");ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
            for(Object o:valores){if(o instanceof List){out.writeInt(((List<?>)o).size());for(Object item:(List<?>)o){Linha l=(Linha)item;out.writeInt(l.produto);out.writeUTF(l.nome);out.writeUTF(l.codigo);out.writeInt(l.quantidade);out.writeUTF(l.preco.toPlainString());out.writeUTF(l.tipo.name());out.writeUTF(l.precoCadastro.toPlainString());}}
                else out.writeUTF(String.valueOf(o));}
            StringBuilder h=new StringBuilder();for(byte v:d.digest(b.toByteArray()))h.append(String.format(Locale.ROOT,"%02x",v&255));return h.toString();
        }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void itens(Connection c,UUID id,List<Linha> itens)throws SQLException{
        try(PreparedStatement d=c.prepareStatement("DELETE FROM venda_suspensa_item WHERE venda_id=?")){d.setString(1,id.toString());d.executeUpdate();}
        try(PreparedStatement s=c.prepareStatement("INSERT INTO venda_suspensa_item(venda_id,linha,id_produto,nome_produto,cod_barras,quantidade,preco_unitario,tipo_venda,preco_cadastro) VALUES(?,?,?,?,?,?,?,?,?)")){
            int i=0;for(Linha l:itens){s.setString(1,id.toString());s.setInt(2,i++);s.setInt(3,l.produto);s.setString(4,l.nome);s.setString(5,l.codigo);s.setInt(6,l.quantidade);s.setBigDecimal(7,l.preco);s.setString(8,l.tipo.name());s.setBigDecimal(9,l.precoCadastro);s.addBatch();}s.executeBatch();
        }
    }
    private void atualizar(Connection c,VendaSuspensa v,UUID operacao,String hash,Estado estado,String token,String nome,List<Linha> itens)throws SQLException{
        try(PreparedStatement st=c.prepareStatement("UPDATE venda_suspensa SET estado=?, versao=versao+1, sessao=?, token=?, identificacao=?, ultima_operacao=?, assinatura=?, atualizada_em=NOW() WHERE id=?")){
            boolean ocupado=token!=null&&(estado==Estado.EM_USO||estado==Estado.EM_PAGAMENTO);
            st.setString(1,estado.name());st.setString(2,ocupado?sessao.id():null);st.setString(3,ocupado?token:null);st.setString(4,nome);st.setString(5,operacao.toString());st.setString(6,hash);st.setString(7,v.id.toString());st.executeUpdate();
        }
        itens(c,v.id,itens);
    }
    @Override public List<VendaSuspensa> listar(){
        try(Connection c=conexoes.get();PreparedStatement st=c.prepareStatement(
                "SELECT id FROM venda_suspensa WHERE estado='SUSPENSA' OR (estado IN ('EM_USO','EM_PAGAMENTO') AND (CASE WHEN sessao IS NULL THEN TRUE ELSE IS_USED_LOCK(CONCAT('pdv_carrinho_',sessao)) IS NULL END)) ORDER BY atualizada_em,id")){
            List<UUID> ids=new ArrayList<>();try(ResultSet rs=st.executeQuery()){while(rs.next())ids.add(UUID.fromString(rs.getString(1)));}
            List<VendaSuspensa> vendas=new ArrayList<>();for(UUID id:ids)vendas.add(ler(c,id,false));return vendas;
        }catch(SQLException e){throw new DbException("Não foi possível carregar as vendas suspensas.",e);}
    }
    @Override public VendaSuspensa gravar(Gravacao g){
        String assinatura=hash(g.id,g.versao,g.token,g.identificacao,g.destino,g.itens,g.liberar);
        return transacao(c->{
            if(g.versao==0){
                if(g.destino!=Estado.SUSPENSA)throw indisponivel();
                try(PreparedStatement st=c.prepareStatement("INSERT INTO venda_suspensa(id,identificacao,versao,estado,ultima_operacao,assinatura) VALUES(?,?,1,'SUSPENSA',?,?) ON DUPLICATE KEY UPDATE id=id")){
                    st.setString(1,g.id.toString());st.setString(2,g.identificacao);st.setString(3,g.operacao.toString());st.setString(4,assinatura);st.executeUpdate();
                }
                VendaSuspensa v=ler(c,g.id,true);
                if(!repetida(c,g.id,g.operacao,assinatura))throw indisponivel();
                if(v.itens.isEmpty()){itens(c,g.id,g.itens);v=ler(c,g.id,false);}return v;
            }
            VendaSuspensa v=ler(c,g.id,true);if(repetida(c,g.id,g.operacao,assinatura))return v;
            meu(v,g.versao,g.token);
            if(g.liberar&&g.destino!=Estado.EM_PAGAMENTO || v.estado!=Estado.EM_USO&&!(v.estado==Estado.EM_PAGAMENTO&&(g.destino==Estado.EM_USO||g.liberar)))throw indisponivel();
            atualizar(c,v,g.operacao,assinatura,g.destino,g.liberar?null:g.token,g.identificacao,g.itens);return ler(c,g.id,false);
        });
    }
    private Map<Integer,Product> produtos(Connection c,UUID id,boolean bloquear)throws SQLException{
        Map<Integer,Product> produtos=new HashMap<>();ProductDaoJdbc dao=new ProductDaoJdbc();
        try(PreparedStatement st=c.prepareStatement("SELECT p.* FROM produto p JOIN venda_suspensa_item i ON i.id_produto=p.id_produto WHERE i.venda_id=? ORDER BY p.id_produto"+(bloquear?" FOR SHARE":""))){
            st.setString(1,id.toString());try(ResultSet rs=st.executeQuery()){while(rs.next()){Product p=dao.instantiate(rs);produtos.put(p.getIdProduto(),p);}}
        }return produtos;
    }
    @Override public RetomadaVenda preparar(UUID id){
        try(Connection c=conexoes.get()){
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            try{VendaSuspensa v=ler(c,id,false);disponivel(c,v,true);RetomadaVenda r=new RetomadaVenda(v,produtos(c,id,false));c.commit();return r;}
            catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException r){e.addSuppressed(r);}throw e;}
        }catch(SQLException e){throw new DbException("Não foi possível conferir o carrinho.",e);}
    }
    @Override public VendaSuspensa retomar(RetomadaVenda p,UUID operacao,UUID token,boolean conferido){
        String assinatura=hash(p.original.id,p.original.versao,token,p.assinatura,conferido);
        return transacao(c->{
            VendaSuspensa v=ler(c,p.original.id,true);if(repetida(c,v.id,operacao,assinatura))return v;
            if(v.versao!=p.original.versao)throw indisponivel();disponivel(c,v,conferido);
            RetomadaVenda atual=new RetomadaVenda(v,produtos(c,v.id,true));
            if(!atual.assinatura.equals(p.assinatura))throw new VendaSuspensaIndisponivelException("Produtos ou estoque mudaram durante a conferência. Atualize e confira a retomada novamente.");
            if(atual.itens.isEmpty())throw new VendaSuspensaIndisponivelException("Nenhum item está disponível. Atualize os produtos ou descarte o carrinho.");
            atualizar(c,v,operacao,assinatura,Estado.EM_USO,token.toString(),v.identificacao,VendaSuspensa.capturar(atual.itens));return ler(c,v.id,false);
        });
    }
    @Override public void descartar(VendaSuspensa v,UUID operacao,boolean conferido){
        String assinatura=hash(v.id,v.versao,"descartar",conferido);
        transacao(c->{VendaSuspensa atual=ler(c,v.id,true);if(repetida(c,v.id,operacao,assinatura))return null;
            if(atual.versao!=v.versao)throw indisponivel();disponivel(c,atual,conferido);
            atualizar(c,atual,operacao,assinatura,Estado.DESCARTADA,null,atual.identificacao,atual.itens);return null;
        });
    }
}
