package com.casarural.sistemapdv.model.dao.impl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.lang.reflect.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RelatorioDaoJdbcResumoTest {
    static Object padrao(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> tipo,InvocationHandler h){return (T)Proxy.newProxyInstance(tipo.getClassLoader(),new Class[]{tipo},h);}
    static ResultSet linhas(List<Map<Object,Object>> dados){int[] index={-1};return proxy(ResultSet.class,(p,m,a)->{
        if(m.getName().equals("next"))return ++index[0]<dados.size();
        if(m.getName().equals("getInt"))return ((Number)dados.get(index[0]).get(a[0])).intValue();
        if(m.getName().equals("getBigDecimal"))return new BigDecimal(dados.get(index[0]).get(a[0]).toString());
        return padrao(m.getReturnType());
    });}
    @Test void daoPreservaPedidoAoRatearDesconto() {
        for(boolean descontoConhecido:new boolean[]{false,true}) {
            Connection conn=proxy(Connection.class,(p,m,a)->{
                if(!m.getName().equals("prepareStatement"))return padrao(m.getReturnType());
                String sql=(String)a[0]; List<Map<Object,Object>> rows;
                if(sql.contains("GROUP BY p.id_pedido")) rows=List.of(
                        Map.of("receita",100,"receita_com_custo",100,"custo",50,"desconto",descontoConhecido?20:0),
                        Map.of("receita",100,"receita_com_custo",0,"custo",0,"desconto",descontoConhecido?0:20));
                else if(sql.contains("COUNT(*),")) rows=List.of(Map.of(1,2,2,180,3,20));
                else if(sql.contains("GROUP BY pg.metodo")) rows=List.of();
                else rows=List.of(Map.of(1,0));
                return proxy(PreparedStatement.class,(stmt,method,args)->method.getName().equals("executeQuery")?linhas(rows):padrao(method.getReturnType()));
            });
            assertEquals(new BigDecimal(descontoConhecido?"30.00":"50.00"),new RelatorioDaoJdbc(()->conn).resumo(LocalDateTime.of(2026,10,1,0,0),LocalDateTime.of(2026,11,1,0,0)).getLucroBruto());
        }
    }
}
