package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.db.DbException;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** DAOs reais, estado confirmado e rollback simulado, sem configuração externa. */
class EstoqueIntegridadeDaoTest {
    static final LocalDateTime DATA=LocalDateTime.of(2026,10,29,12,0);
    static BigDecimal dinheiro(String v){return new BigDecimal(v);}
    static Object def(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;return null;}
    static <T>T proxy(Class<T> t,InvocationHandler h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class[]{t},h));}
    static class Banco {
        Product produto=new Product("1","Arroz",dinheiro("2.00"),10);
        Integer baixa; boolean cancelado; int consultas, reposicoes;
        Object[] meta(){return new Object[]{produto.getCodBarras(),produto.getNomeProduto(),produto.getPrecoProduto(),produto.getEstoque(),produto.isPermiteFiado(),produto.getTipoVenda().name(),produto.isControlaEstoque(),produto.getPrecoCusto(),produto.isAtivo()};}
        Connection conectar(){
            int inicial=produto.getEstoque();Integer baixaInicial=baixa;boolean canceladoInicial=cancelado;
            return proxy(Connection.class,(p,m,a)->{
                switch(m.getName()){
                    case "prepareStatement":return statement((String)a[0]);
                    case "rollback":produto.setEstoque(inicial);baixa=baixaInicial;cancelado=canceladoInicial;return null;
                    default:return def(m.getReturnType());
                }
            });
        }
        PreparedStatement statement(String sql){
            Map<Integer,Object> params=new HashMap<>();List<Map<Integer,Object>> batch=new ArrayList<>();
            return proxy(PreparedStatement.class,(p,m,a)->{
                String n=m.getName();
                if(n.startsWith("set")){params.put((Integer)a[0],a[1]);return null;}
                if(n.equals("addBatch")){batch.add(new HashMap<>(params));return null;}
                if(n.equals("getGeneratedKeys"))return rows(List.of(Map.of("1",1)));
                if(n.equals("executeQuery")){
                    consultas++;
                    if(sql.startsWith("SELECT id_caixa"))return rows(List.of(Map.of("1",1,"aberto_em",Timestamp.valueOf(DATA.minusHours(1)))));
                    if(sql.startsWith("SELECT aberto_em"))return rows(List.of(Map.of("1",Timestamp.valueOf(DATA.minusHours(1)))));
                    if(sql.startsWith("SELECT status"))return rows(List.of(Map.of("status",cancelado?"CANCELADO":"PAGO","data_pedido",Timestamp.valueOf(DATA),"valor_total",dinheiro("2.00"))));
                    if(sql.startsWith("SELECT id_produto, COALESCE"))return rows(List.of(Map.of("id_produto",1,"repor",BigDecimal.valueOf(baixa==null?0:baixa),"legados",baixa==null?1:0)));
                    return rows(List.of());
                }
                if(n.equals("executeBatch")){
                    if(sql.startsWith("INSERT INTO itempedido")) {
                        assertTrue(sql.contains("estoque_baixado"));assertTrue(sql.contains("IF(controla_estoque"));
                        for(Map<Integer,Object> fatia:batch)baixa=produto.isControlaEstoque()?(Integer)fatia.get(6):0;
                    }
                    return new int[]{1};
                }
                if(n.equals("executeUpdate")){
                    if(sql.startsWith("UPDATE produto SET cod_barras")){
                        assertTrue(sql.contains("estoque <=> ?"));assertTrue(sql.contains("preco_custo <=> ?"));
                        Object[] atual=meta();for(int i=0;i<atual.length;i++)if(!Objects.equals(atual[i],params.get(11+i)))return 0;
                        produto.setNomeProduto((String)params.get(2));produto.setEstoque((Integer)params.get(4));return 1;
                    }
                    if(sql.startsWith("UPDATE produto SET estoque = IF")){
                        int qtd=(Integer)params.get(1);if(produto.isControlaEstoque()){if(produto.getEstoque()<qtd)return 0;produto.setEstoque(produto.getEstoque()-qtd);}return 1;
                    }
                    if(sql.startsWith("UPDATE produto SET estoque = estoque +")){
                        assertFalse(sql.contains("controla_estoque"));
                        int qtd=(Integer)params.get(1);if(produto.getEstoque()>(Integer)params.get(4))return 0;
                        produto.setEstoque(Math.addExact(produto.getEstoque(),qtd));reposicoes++;return 1;
                    }
                    if(sql.startsWith("UPDATE pedido SET status"))cancelado=true;
                    return 1;
                }
                return def(m.getReturnType());
            });
        }
        ResultSet rows(List<Map<String,Object>> dados){int[] i={-1};return proxy(ResultSet.class,(p,m,a)->{
            if(m.getName().equals("next"))return ++i[0]<dados.size();if(m.getName().equals("close"))return null;
            Object v=dados.get(i[0]).get(String.valueOf(a[0]));
            if(m.getName().equals("getInt"))return ((Number)v).intValue();
            if(m.getName().equals("getString"))return v.toString();return v;
        });}
        Order venda(){produto.setIdProduto(1);Order o=new Order();o.setStatus(OrderStatus.PAGO);o.setItemPedido(List.of(new OrderItem(produto,1,dinheiro("2.00"))));o.aplicarDesconto(BigDecimal.ZERO);return o;}
        Product copia(){Product p=new Product(produto.getCodBarras(),produto.getNomeProduto(),produto.getPrecoProduto(),produto.getEstoque());p.setIdProduto(1);p.copiarEstadoOriginal(produto);return p;}
    }
    @Test void edicaoAntigaNaoDesfazVendaNemEntradaConcorrente(){
        for(int saldo:new int[]{9,15}){
            Banco b=new Banco();Product edit=b.copia();edit.setNomeProduto("Nome editado");b.produto.setEstoque(saldo);
            assertThrows(DbException.class,()->new ProductDaoJdbc(b::conectar).update(edit));assertEquals(saldo,b.produto.getEstoque());assertEquals("Arroz",b.produto.getNomeProduto());
        }
    }
    @Test void edicaoConcorrenteDeMetadadosTambemExigeReabrir(){Banco b=new Banco();Product edit=b.copia();b.produto.setPrecoCusto(dinheiro("3.00"));assertThrows(DbException.class,()->new ProductDaoJdbc(b::conectar).update(edit));assertEquals(dinheiro("3.00"),b.produto.getPrecoCusto());}
    @Test void edicaoAtualPodeAjustarEstoqueEAtualizaReferencia(){Banco b=new Banco();Product edit=b.copia();edit.setEstoque(7);new ProductDaoJdbc(b::conectar).update(edit);assertEquals(7,b.produto.getEstoque());assertEquals(7,edit.getEstadoOriginal()[3]);}
    @Test void desligarControleDepoisDaVendaAindaRepoeBaixa(){Banco b=new Banco();OrderDaoJdbc dao=new OrderDaoJdbc(b::conectar, java.time.Clock.fixed(DATA.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault()));dao.insert(b.venda());assertEquals(9,b.produto.getEstoque());assertEquals(1,b.baixa);b.produto.setControlaEstoque(false);dao.cancel(1,"teste");assertEquals(10,b.produto.getEstoque());assertTrue(b.cancelado);}
    @Test void ligarControleDepoisDaVendaNaoCriaEstoque(){Banco b=new Banco();b.produto.setControlaEstoque(false);OrderDaoJdbc dao=new OrderDaoJdbc(b::conectar, java.time.Clock.fixed(DATA.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault()));dao.insert(b.venda());assertEquals(0,b.baixa);b.produto.setControlaEstoque(true);dao.cancel(1,"teste");assertEquals(10,b.produto.getEstoque());assertEquals(0,b.reposicoes);}
    @Test void legadoEOverflowNaoConfirmamCancelamento(){
        Banco legado=new Banco();assertThrows(DbException.class,()->new OrderDaoJdbc(legado::conectar, java.time.Clock.fixed(DATA.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault())).cancel(1,"teste"));assertFalse(legado.cancelado);assertEquals(10,legado.produto.getEstoque());
        Banco cheio=new Banco();cheio.baixa=1;cheio.produto.setEstoque(Integer.MAX_VALUE);assertThrows(DbException.class,()->new OrderDaoJdbc(cheio::conectar, java.time.Clock.fixed(DATA.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault())).cancel(1,"teste"));assertEquals(Integer.MAX_VALUE,cheio.produto.getEstoque());assertFalse(cheio.cancelado);
    }
    @Test void cancelamentoConfirmadoNaoRepoeDuasVezes(){Banco b=new Banco();b.baixa=2;b.produto.setEstoque(8);OrderDaoJdbc dao=new OrderDaoJdbc(b::conectar, java.time.Clock.fixed(DATA.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault()));dao.cancel(1,"teste");assertThrows(DbException.class,()->dao.cancel(1,"teste"));assertEquals(10,b.produto.getEstoque());assertEquals(1,b.reposicoes);}
}
