package com.casarural.sistemapdv.model.dao.impl;

import com.casarural.sistemapdv.model.entities.*;
import com.casarural.sistemapdv.model.entities.enums.*;
import com.casarural.sistemapdv.util.Money;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Usado dentro da transação que já bloqueou caixa e pedido. */
final class DevolucaoVendaJdbc {
    private DevolucaoVendaJdbc(){}
    static void registrar(Connection c,int pedido,int caixa,String tipo,String status,BigDecimal total,String motivo,
                          Map<PaymentMethod,BigDecimal> pago,Order nova)throws SQLException {
        Map<PaymentMethod,BigDecimal> devolver=new EnumMap<>(PaymentMethod.class);
        if(nova==null)pago.forEach((m,v)->{if(m!=PaymentMethod.FIADO)devolver.put(m,v);});
        else {
            RegraTroca.Resultado regra=RegraTroca.calcular(pago,nova.getValorTotal());
            Map<PaymentMethod,BigDecimal> esperados=new EnumMap<>(PaymentMethod.class),informados=new EnumMap<>(PaymentMethod.class);
            regra.getCreditos().forEach(p->esperados.merge(p.getMetodo(),p.getValor(),BigDecimal::add));
            nova.getPagamentos().stream().filter(Order.PaymentSlice::isCredito).forEach(p->informados.merge(p.getMetodo(),p.getValor(),BigDecimal::add));
            if(!esperados.equals(informados))throw new SQLException("Os créditos da troca mudaram. Reabra a venda pelo histórico.");
            devolver.putAll(regra.getDevolver());
        }
        BigDecimal novos=nova==null?Money.ZERO:nova.getPagamentos().stream()
                .filter(p->!p.isCredito()&&p.getMetodo()==PaymentMethod.DINHEIRO).map(Order.PaymentSlice::getValor).reduce(Money.ZERO,BigDecimal::add);
        BigDecimal dinheiro=devolver.getOrDefault(PaymentMethod.DINHEIRO,Money.ZERO);
        if(Money.isPositive(dinheiro)) {
            CashRegister atual=new CashRegister();atual.setIdCaixa(caixa);
            try(PreparedStatement st=c.prepareStatement("SELECT valor_inicial FROM caixa WHERE id_caixa=?")){
                st.setInt(1,caixa);try(ResultSet rs=st.executeQuery()){if(!rs.next())throw new SQLException("Caixa não encontrado.");atual.setValorInicial(rs.getBigDecimal(1));}
            }
            BigDecimal disponivel=new CashRegisterDaoJdbc().summarize(c,atual).getDinheiroEsperado().add(novos);
            if(dinheiro.compareTo(disponivel)>0)throw new SQLException("Não há dinheiro suficiente no caixa para devolver "+Money.format(dinheiro)+". Registre um reforço de caixa antes de confirmar.");
        }
        try(PreparedStatement st=c.prepareStatement("INSERT INTO devolucao_venda(id_pedido,id_caixa,tipo,status_original,valor_total,fiado_baixado,motivo) VALUES(?,?,?,?,?,?,?)")){
            st.setInt(1,pedido);st.setInt(2,caixa);st.setString(3,tipo);st.setString(4,status);st.setBigDecimal(5,total);
            st.setBigDecimal(6,pago.getOrDefault(PaymentMethod.FIADO,Money.ZERO));st.setString(7,motivo);st.executeUpdate();
        }
        try(PreparedStatement st=c.prepareStatement("INSERT INTO devolucao_venda_forma(id_pedido,metodo,valor_compensado,valor_devolvido) VALUES(?,?,?,?)")){
            for(PaymentMethod m:PaymentMethod.values())if(m!=PaymentMethod.FIADO&&Money.isPositive(pago.get(m))){
                st.setInt(1,pedido);st.setString(2,m.name());st.setBigDecimal(3,pago.get(m));st.setBigDecimal(4,devolver.getOrDefault(m,Money.ZERO));st.addBatch();
            }st.executeBatch();
        }
    }
    static List<DevolucaoVenda> carregar(Connection c,int caixa)throws SQLException {
        List<DevolucaoVenda> lista=new ArrayList<>();
        try(PreparedStatement st=c.prepareStatement("SELECT * FROM devolucao_venda WHERE id_caixa=? ORDER BY registrada_em,id_pedido")){
            st.setInt(1,caixa);try(ResultSet rs=st.executeQuery()){while(rs.next()){
                int pedido=rs.getInt("id_pedido");Map<PaymentMethod,BigDecimal> compensado=new EnumMap<>(PaymentMethod.class),devolvido=new EnumMap<>(PaymentMethod.class);
                try(PreparedStatement formas=c.prepareStatement("SELECT * FROM devolucao_venda_forma WHERE id_pedido=?")){
                    formas.setInt(1,pedido);try(ResultSet f=formas.executeQuery()){while(f.next()){
                        PaymentMethod m=PaymentMethod.valueOf(f.getString("metodo"));compensado.put(m,Money.of(f.getBigDecimal("valor_compensado")));
                        if(Money.isPositive(f.getBigDecimal("valor_devolvido")))devolvido.put(m,Money.of(f.getBigDecimal("valor_devolvido")));
                    }}
                }
                lista.add(new DevolucaoVenda(pedido,rs.getString("tipo"),rs.getString("motivo"),rs.getTimestamp("registrada_em").toLocalDateTime(),Money.of(rs.getBigDecimal("fiado_baixado")),compensado,devolvido));
            }}
        }return lista;
    }
    static void compensar(Connection c,int caixa,CashSummary resumo)throws SQLException {
        try(PreparedStatement st=c.prepareStatement("SELECT f.metodo,SUM(f.valor_compensado) AS total FROM devolucao_venda_forma f JOIN devolucao_venda d ON d.id_pedido=f.id_pedido WHERE d.id_caixa=? GROUP BY f.metodo")){
            st.setInt(1,caixa);try(ResultSet rs=st.executeQuery()){while(rs.next())resumo.addCompensacao(PaymentMethod.valueOf(rs.getString("metodo")),rs.getBigDecimal("total"));}
        }
    }
}
