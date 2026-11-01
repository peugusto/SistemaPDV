package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.DB;
import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.ArquivoComprovanteDao;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado;
import com.casarural.sistemapdv.model.entities.ComprovanteGuardado.Tipo;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

public final class ArquivoComprovanteDaoJdbc implements ArquivoComprovanteDao {
    private final Supplier<Connection> conexoes;
    public ArquivoComprovanteDaoJdbc() { this(DB::getConnection); }
    public ArquivoComprovanteDaoJdbc(Supplier<Connection> conexoes) { this.conexoes = Objects.requireNonNull(conexoes); }
    public ComprovanteGuardado guardar(ComprovanteGuardado d) {
        try (Connection c = conexoes.get(); PreparedStatement st = c.prepareStatement(
                "INSERT INTO comprovante_impressao(tipo,referencia,nome_trabalho,texto) VALUES(?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE referencia=referencia")) {
            st.setString(1,d.getTipo().name()); st.setString(2,d.getReferencia());
            st.setString(3,d.getNomeTrabalho()); st.setString(4,d.getTexto()); st.executeUpdate();
            return buscar(c,d.getTipo(),d.getReferencia()).orElseThrow(() -> new DbException("Comprovante não encontrado após arquivar."));
        } catch (SQLException e) { throw new DbException("Não foi possível guardar o comprovante. A operação financeira não deve ser repetida.",e); }
    }
    public Optional<ComprovanteGuardado> buscar(Tipo tipo,String referencia) {
        try (Connection c=conexoes.get()) { return buscar(c,tipo,referencia); }
        catch(SQLException e) { throw new DbException("Não foi possível consultar a segunda via.",e); }
    }
    private Optional<ComprovanteGuardado> buscar(Connection c,Tipo tipo,String referencia) throws SQLException {
        try(PreparedStatement st=c.prepareStatement("SELECT * FROM comprovante_impressao WHERE tipo=? AND referencia=?")) {
            st.setString(1,tipo.name());st.setString(2,referencia);
            try(ResultSet rs=st.executeQuery()) { return rs.next()?Optional.of(ler(rs)):Optional.empty(); }
        }
    }
    public Optional<ComprovanteGuardado> ultimaLista() {
        try(Connection c=conexoes.get();PreparedStatement st=c.prepareStatement(
                "SELECT * FROM comprovante_impressao WHERE tipo='LISTA_COMPRAS' ORDER BY id_comprovante DESC LIMIT 1"); ResultSet rs=st.executeQuery()) {
            return rs.next()?Optional.of(ler(rs)):Optional.empty();
        } catch(SQLException e) { throw new DbException("Não foi possível consultar a última lista impressa.",e); }
    }
    private ComprovanteGuardado ler(ResultSet rs) throws SQLException {
        return new ComprovanteGuardado(Tipo.valueOf(rs.getString("tipo")),rs.getString("referencia"),rs.getString("nome_trabalho"),rs.getString("texto"));
    }
}
