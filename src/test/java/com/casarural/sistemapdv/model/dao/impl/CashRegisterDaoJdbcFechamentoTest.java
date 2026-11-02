package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.db.*;
import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.CashMovementType;
import com.casarural.sistemapdv.services.CashRegisterService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CashRegisterDaoJdbcFechamentoTest {
    static BigDecimal r(String s){return new BigDecimal(s).setScale(2);}
    static <T>T proxy(Class<T> t,InvocationHandler h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h));}
    static class Banco {
        final List<String> eventos=new ArrayList<>();BigDecimal esperado;boolean fechado,erroResumo,erroUpdate,erroCommit,erroRollback,erroClose;
        int conexoes;BigDecimal retirado=r("10");
        CashRegisterDaoJdbc dao(){return new CashRegisterDaoJdbc(this::conectar);}
        Connection conectar(){conexoes++;return proxy(Connection.class,(p,m,a)->{
            switch(m.getName()) {
                case "prepareStatement":return statement((String)a[0]);
                case "setAutoCommit":eventos.add("autocommit="+a[0]);return null;
                case "commit":eventos.add("commit");if(erroCommit)throw new SQLException("COMMIT SINTETICO");return null;
                case "rollback":eventos.add("rollback");if(erroRollback)throw new SQLException("ROLLBACK SINTETICO");return null;
                case "abort":eventos.add("abort");return null;
                case "close":eventos.add("close");if(erroClose)throw new SQLException("CLOSE SINTETICO");return null;
                default:throw new AssertionError(m.getName());
            }
        });}
        PreparedStatement statement(String sql){Map<Integer,Object> params=new HashMap<>();return proxy(PreparedStatement.class,(p,m,a)->{
            String n=m.getName();if(n.startsWith("set")){params.put((Integer)a[0],a[1]);return null;}if(n.equals("close"))return null;
            if(n.equals("executeUpdate")){eventos.add("update");if(erroUpdate)throw new SQLException("UPDATE SINTETICO");if(sql.startsWith("UPDATE caixa"))esperado=(BigDecimal)params.get(2);else if("SANGRIA".equals(params.get(2)))retirado=retirado.add((BigDecimal)params.get(3));return 1;}
            if(!n.equals("executeQuery"))throw new AssertionError(n);
            if(sql.startsWith("SELECT * FROM caixa")) {
                assertTrue(sql.endsWith("FOR UPDATE"));eventos.add("lock");Map<String,Object> row=new HashMap<>();
                row.put("id_caixa",1);row.put("aberto_em",Timestamp.valueOf(LocalDateTime.of(2026,11,1,8,0)));row.put("valor_inicial",r("100"));
                row.put("fechado_em",fechado?Timestamp.valueOf(LocalDateTime.of(2026,11,1,18,0)):null);return rows(List.of(row));
            }
            if(sql.contains("FROM pagamento")){eventos.add("pagamentos");if(erroResumo)throw new SQLException("RESUMO SINTETICO");return rows(List.of(Map.of("origem","VENDA","metodo_pagamento","DINHEIRO","total",r("20"))));}
            if(sql.contains("FROM movimento_caixa")){eventos.add("movimentos");return rows(List.of(Map.of("tipo","SANGRIA","total",retirado)));}
            if(sql.contains("FROM devolucao_venda_forma"))return rows(List.of());
            throw new AssertionError(sql);
        });}
        ResultSet rows(List<Map<String,Object>> dados){int[] i={-1};return proxy(ResultSet.class,(p,m,a)->{
            if(m.getName().equals("next"))return ++i[0]<dados.size();if(m.getName().equals("close"))return null;return dados.get(i[0]).get(a[0]);
        });}
    }
    static CashRegister tela(){CashRegister c=new CashRegister();c.setIdCaixa(1);c.setValorInicial(r("999"));return c;}
    @Test void calculaDepoisDoLockComValorInicialDoBancoEDevolveFechamentoConfirmado() {
        Banco b=new Banco();CashRegister c=tela();new CashRegisterService(b.dao()).close(c,r("110")," conferido ");
        assertEquals(r("110"),b.esperado);assertEquals(r("100"),c.getValorInicial());assertFalse(c.isAberto());
        assertEquals(r("110"),c.getValorEsperado());assertEquals(r("0"),c.getDiferenca());assertEquals("conferido",c.getObservacao());
        assertEquals(1,b.conexoes);assertEquals(List.of("autocommit=false","lock","pagamentos","movimentos","update","commit","autocommit=true","close"),b.eventos);
    }
    @Test void semConferenciaPreservaContadoNulo(){Banco b=new Banco();CashRegister c=new CashRegisterService(b.dao()).close(tela(),null,null);assertNull(c.getValorContado());assertNull(c.getDiferenca());}
    @Test void falhaAntesDoCommitNaoMudaObjetoDaTelaENaoConfirma(){
        for(boolean resumo:List.of(true,false)){Banco b=new Banco();b.erroResumo=resumo;b.erroUpdate=!resumo;CashRegister c=tela();
            assertThrows(DbException.class,()->new CashRegisterService(b.dao()).close(c,null,null));assertTrue(c.isAberto());assertNull(c.getValorEsperado());
            assertTrue(b.eventos.contains("rollback"));assertFalse(b.eventos.contains("commit"));}
    }
    @Test void caixaJaFechadoNaoResumeNemEscreve(){Banco b=new Banco();b.fechado=true;assertThrows(DbException.class,()->b.dao().close(1,null,null));assertFalse(b.eventos.contains("pagamentos"));assertFalse(b.eventos.contains("update"));}
    @Test void movimentoAposFechamentoTambemRespeitaLock(){Banco b=new Banco();b.fechado=true;assertThrows(DbException.class,()->b.dao().addMovement(1,CashMovementType.REFORCO,r("10"),"FICTICIO"));assertFalse(b.eventos.contains("update"));}
    @Test void falhaDeLimpezaNaoDesfazSucesso(){Banco b=new Banco();b.erroClose=true;assertFalse(b.dao().close(1,null,null).isAberto());assertTrue(b.eventos.contains("commit"));}
    @Test void commitSemRespostaNaoAutorizaRepeticao(){Banco b=new Banco();b.erroCommit=true;b.erroRollback=true;assertThrows(ResultadoTransacaoIncertoException.class,()->b.dao().close(1,null,null));assertTrue(b.eventos.contains("abort"));assertFalse(b.eventos.contains("autocommit=true"));}
    @Test void sangriaRecalculaSaldoComLockMesmoComObjetoDaTelaAntigo() {
        Banco b=new Banco();CashRegisterService s=new CashRegisterService(b.dao());CashRegister antigo=tela();
        s.addSangria(antigo,r("80"),"FICTICIO");int escritas=Collections.frequency(b.eventos,"update");
        DbException erro=assertThrows(DbException.class,()->s.addSangria(antigo,r("80"),"FICTICIO"));
        assertTrue(erro.getMessage().contains("30,00"));assertEquals(escritas,Collections.frequency(b.eventos,"update"));
        assertEquals(2,Collections.frequency(b.eventos,"lock"));assertEquals(1,Collections.frequency(b.eventos,"commit"));
    }
    @Test void sangriaPodeZerarSaldoMasNaoPassarDele() {
        Banco b=new Banco();b.dao().addMovement(1,CashMovementType.SANGRIA,r("110"),"FICTICIO");
        assertThrows(DbException.class,()->b.dao().addMovement(1,CashMovementType.SANGRIA,r("0.01"),"FICTICIO"));
        assertEquals(1,Collections.frequency(b.eventos,"update"));
    }
    @Test void movimentoInvalidoNaoAbreConexao() {
        Banco b=new Banco();for(BigDecimal v:Arrays.asList(null,r("0"),r("-1"),new BigDecimal("0.001")))
            assertThrows(DbException.class,()->b.dao().addMovement(1,CashMovementType.SANGRIA,v,"FICTICIO"));
        assertEquals(0,b.conexoes);
    }
}
