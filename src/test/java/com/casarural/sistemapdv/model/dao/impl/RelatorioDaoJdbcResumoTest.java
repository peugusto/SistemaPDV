package com.casarural.sistemapdv.model.dao.impl;
import com.casarural.sistemapdv.model.entities.ResumoVendas;
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
        if(m.getName().equals("getBoolean"))return dados.get(index[0]).get(a[0]);
        if(m.getName().equals("getBigDecimal"))return new BigDecimal(dados.get(index[0]).get(a[0]).toString());
        return padrao(m.getReturnType());
    });}
    @Test void daoPreservaPedidoAoRatearDesconto() {
        for(boolean descontoConhecido:new boolean[]{false,true}) {
            ResumoVendas resumo = resumo(List.of(
                    custo(100, 100, 50, descontoConhecido ? 20 : 0, true, false),
                    custo(100, 0, 0, descontoConhecido ? 0 : 20, false, true)));
            assertEquals(new BigDecimal(descontoConhecido?"30.00":"50.00"), resumo.getLucroBruto());
        }
    }

    @Test void devolucaoComCustoNaoOcultaVendaSemCusto() {
        ResumoVendas resumo = resumo(List.of(
                custo(-100, -100, -60, 0, true, false),
                custo(200, 0, 0, 0, false, true)));
        assertEquals(new BigDecimal("-40.00"), resumo.getLucroBruto());
        assertEquals(new BigDecimal("33.3"), resumo.getCoberturaCusto());
        assertTrue(resumo.temCustoParcial());
    }

    @Test void valoresLiquidosZeradosNaoEscondemCustosEItemGratuitoSemCusto() {
        ResumoVendas resumo = resumo(List.of(
                custo(100, 100, 60, 0, true, true),
                custo(-100, -100, -60, 0, true, false)));
        assertEquals(BigDecimal.ZERO.setScale(2), resumo.getLucroBruto());
        assertEquals(new BigDecimal("100.0"), resumo.getCoberturaCusto());
        assertTrue(resumo.temReceitaComCusto());
        assertTrue(resumo.temCustoParcial());
    }

    private static Map<Object, Object> custo(int receita, int receitaComCusto, int custo, int desconto,
                                            boolean temConhecido, boolean temDesconhecido) {
        return Map.of("receita", receita, "receita_com_custo", receitaComCusto, "custo", custo, "desconto", desconto,
                "tem_itens_com_custo", temConhecido, "tem_itens_sem_custo", temDesconhecido);
    }

    private ResumoVendas resumo(List<Map<Object, Object>> custos) {
        Connection conn = proxy(Connection.class, (p, m, a) -> {
            if (!m.getName().equals("prepareStatement")) return padrao(m.getReturnType());
            String sql = (String) a[0];
            List<Map<Object, Object>> rows;
            if (sql.contains("GROUP BY p.id_pedido")) rows = custos;
            else if (sql.contains("FROM movimento_venda_relatorio p") && !sql.contains("JOIN")) rows = List.of(Map.of(1, 2, 2, 180, 3, 20));
            else if (sql.contains("GROUP BY pg.metodo")) rows = List.of();
            else rows = List.of(Map.of(1, 0));
            return proxy(PreparedStatement.class, (stmt, method, args) ->
                    method.getName().equals("executeQuery") ? linhas(rows) : padrao(method.getReturnType()));
        });
        return new RelatorioDaoJdbc(() -> conn).resumo(LocalDateTime.of(2026,10,1,0,0), LocalDateTime.of(2026,11,1,0,0));
    }
}
