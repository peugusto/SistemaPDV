package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.dao.AjusteEstoqueDao;
import com.casarural.sistemapdv.model.entities.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

public class AjusteEstoqueDaoJdbc implements AjusteEstoqueDao {
    private final Supplier<Connection> conexoes;
    public AjusteEstoqueDaoJdbc(){this(DB::getConnection);}
    public AjusteEstoqueDaoJdbc(Supplier<Connection> conexoes){this.conexoes=Objects.requireNonNull(conexoes);}
    @Override public RegistroAjusteEstoque registrar(AjusteEstoque a){
        Objects.requireNonNull(a,"Ajuste não informado.");
        return TransacaoJdbc.executar(conexoes,"registrar ajuste de estoque",c->{
            String nome,codigo;int saldo;long repor;boolean controla;
            try(PreparedStatement st=c.prepareStatement("SELECT nome_produto,cod_barras,estoque,quantidade_repor,controla_estoque,tipo_venda FROM produto WHERE id_produto=? FOR UPDATE")){
                st.setInt(1,a.produto);try(ResultSet rs=st.executeQuery()){
                    if(!rs.next())throw new DbException("Produto não encontrado. Reabra o estoque.");
                    nome=rs.getString("nome_produto");codigo=rs.getString("cod_barras");saldo=rs.getInt("estoque");repor=rs.getLong("quantidade_repor");controla=rs.getBoolean("controla_estoque")&&!"PESAVEL".equals(rs.getString("tipo_venda"));
                }
            }
            RegistroAjusteEstoque existente=buscar(c,a.operacao);
            if(existente!=null){if(!a.mesmosDados(existente.ajuste))throw new DbException("A mesma tentativa não pode ser confirmada com outro conteúdo.");return existente;}
            if(!controla)throw new DbException("Este produto não controla estoque por unidade. Confira o cadastro antes de ajustar.");
            if(saldo!=a.saldoEsperado)throw new DbException("O estoque mudou desde a conferência. Nenhum ajuste foi aplicado. Reabra a operação para conferir o saldo atual.");
            long novoRepor=a.reposicaoDepois(repor);
            try(PreparedStatement st=c.prepareStatement("UPDATE produto SET estoque=?,quantidade_repor=? WHERE id_produto=?")){
                st.setInt(1,a.saldoDepois());st.setLong(2,novoRepor);st.setInt(3,a.produto);if(st.executeUpdate()!=1)throw new SQLException("Produto não atualizado.");
            }
            inserir(c,a,nome,codigo,repor,novoRepor);return Objects.requireNonNull(buscar(c,a.operacao));
        });
    }
    static void inserir(Connection c,AjusteEstoque a,String nome,String codigo,long reporAntes,long reporDepois)throws SQLException{
        try(PreparedStatement st=c.prepareStatement("INSERT INTO ajuste_estoque(id_operacao,id_produto,tipo,saldo_esperado,valor_informado,saldo_antes,saldo_depois,reposicao_antes,reposicao_depois,motivo,nome_produto,cod_barras) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")){
            st.setString(1,a.operacao.toString());st.setInt(2,a.produto);st.setString(3,a.tipo.name());st.setInt(4,a.saldoEsperado);st.setInt(5,a.informado);st.setInt(6,a.saldoEsperado);st.setInt(7,a.saldoDepois());st.setLong(8,reporAntes);st.setLong(9,reporDepois);st.setString(10,a.motivo);st.setString(11,nome);st.setString(12,codigo);st.executeUpdate();
        }
    }
    private static RegistroAjusteEstoque buscar(Connection c,UUID id)throws SQLException{
        try(PreparedStatement st=c.prepareStatement("SELECT * FROM ajuste_estoque WHERE id_operacao=? FOR UPDATE")){
            st.setString(1,id.toString());try(ResultSet rs=st.executeQuery()){return rs.next()?ler(rs):null;}
        }
    }
    private static RegistroAjusteEstoque ler(ResultSet r)throws SQLException{
        AjusteEstoque a=new AjusteEstoque(UUID.fromString(r.getString("id_operacao")),r.getInt("id_produto"),AjusteEstoque.Tipo.valueOf(r.getString("tipo")),r.getInt("saldo_esperado"),r.getInt("valor_informado"),r.getString("motivo"));
        return new RegistroAjusteEstoque(a,r.getString("nome_produto"),r.getString("cod_barras"),r.getInt("saldo_antes"),r.getInt("saldo_depois"),r.getLong("reposicao_antes"),r.getLong("reposicao_depois"),r.getTimestamp("registrado_em").toLocalDateTime());
    }
    @Override public List<RegistroAjusteEstoque> historico(int produto,int limite){
        if(produto<=0||limite<1||limite>500)throw new IllegalArgumentException("Selecione um produto e um limite válido.");
        try(Connection c=conexoes.get();PreparedStatement st=c.prepareStatement("SELECT * FROM ajuste_estoque WHERE id_produto=? ORDER BY registrado_em DESC,id_ajuste DESC LIMIT ?")){
            st.setInt(1,produto);st.setInt(2,limite);List<RegistroAjusteEstoque> registros=new ArrayList<>();try(ResultSet rs=st.executeQuery()){while(rs.next())registros.add(ler(rs));}return registros;
        }catch(SQLException e){throw new DbException("Não foi possível carregar o histórico de ajustes.",e);}
    }
    /** A edição de cadastro também precisa justificar e registrar qualquer mudança do saldo. */
    static void registrarEdicao(Connection c,Product p,int antes,String motivo)throws SQLException{
        AjusteEstoque a=new AjusteEstoque(UUID.randomUUID(),p.getIdProduto(),AjusteEstoque.Tipo.CONTAGEM,antes,p.getEstoque(),motivo);
        long repor;try(PreparedStatement st=c.prepareStatement("SELECT quantidade_repor FROM produto WHERE id_produto=? FOR UPDATE")){
            st.setInt(1,p.getIdProduto());try(ResultSet rs=st.executeQuery()){if(!rs.next())throw new SQLException("Produto não encontrado.");repor=rs.getLong(1);}
        }
        long depois=a.reposicaoDepois(repor);
        try(PreparedStatement st=c.prepareStatement("UPDATE produto SET quantidade_repor=? WHERE id_produto=?")){st.setLong(1,depois);st.setInt(2,p.getIdProduto());st.executeUpdate();}
        inserir(c,a,p.getNomeProduto(),p.getCodBarras(),repor,depois);
    }
}
