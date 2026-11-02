package com.casarural.sistemapdv.db;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PrazosBancoTest {
    static <T>T proxy(Class<T> tipo,InvocationHandler h){return tipo.cast(Proxy.newProxyInstance(tipo.getClassLoader(),new Class<?>[]{tipo},h));}
    @Test void urlAntigaNaoDesativaLimitesNemReligaRetryDeEscritas() throws Exception {
        String url=PrazosBanco.url("jdbc:mysql://127.0.0.1:1/SINTETICO?socketTimeout=0&%63onnectTimeout=999999&autoReconnect=true&autoReconnectForPools=true&reconnectAtTxEnd=true&useSSL=false",false);
        assertFalse(url.contains("999999"));assertFalse(url.contains("Timeout=0"));assertTrue(url.contains("useSSL=false"));
        Map<String,String> efetivas=new HashMap<>();
        for(DriverPropertyInfo p:DriverManager.getDriver(url).getPropertyInfo(url,new Properties()))efetivas.put(p.name,p.value);
        assertEquals("3000",efetivas.get("connectTimeout"));assertEquals("10000",efetivas.get("socketTimeout"));
        assertEquals("false",efetivas.get("autoReconnect"));assertEquals("true",efetivas.get("queryTimeoutKillsConnection"));
        assertEquals("false",efetivas.get("autoReconnectForPools"));assertEquals("false",efetivas.get("reconnectAtTxEnd"));
        assertEquals(url,PrazosBanco.url(url,false));assertTrue(PrazosBanco.url(url,true).contains("socketTimeout=60000"));
    }
    @Test void statementNormalEPreparadoRecebemPrazoENaoMudamExcecaoOriginal() throws Exception {
        List<Integer> prazos=new ArrayList<>();SQLException erro=new SQLTimeoutException("SINTETICO");
        PreparedStatement st=proxy(PreparedStatement.class,(p,m,a)->{if(m.getName().equals("setQueryTimeout")){prazos.add((Integer)a[0]);return null;}if(m.getName().equals("executeQuery"))throw erro;return null;});
        List<Integer> rede=new ArrayList<>();
        Connection original=proxy(Connection.class,(p,m,a)->{if(m.getName().equals("setNetworkTimeout")){rede.add((Integer)a[1]);return null;}return st;});
        Connection c=PrazosBanco.limitar(original);c.createStatement();PreparedStatement ps=c.prepareStatement("SELECT 1");
        assertEquals(List.of(10000),rede);assertEquals(List.of(8,8),prazos);assertSame(erro,assertThrows(SQLException.class,ps::executeQuery));
    }
    @Test void falhaAoConfigurarRedeDevolveConexao() {
        boolean[] fechou={false};Connection c=proxy(Connection.class,(p,m,a)->{if(m.getName().equals("close")){fechou[0]=true;return null;}throw new SQLException("SINTETICO");});
        assertThrows(SQLException.class,()->PrazosBanco.limitar(c));assertTrue(fechou[0]);
    }
}
