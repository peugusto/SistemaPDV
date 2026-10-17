package com.casarural.sistemapdv.services;

import com.casarural.sistemapdv.db.DbException;
import com.casarural.sistemapdv.model.dao.CustomerDao;
import com.casarural.sistemapdv.model.entities.Customer;
import com.casarural.sistemapdv.model.entities.enums.CustomerStatus;
import com.casarural.sistemapdv.util.Money;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class CustomerService {
    private CustomerDao dao;

    public CustomerService(CustomerDao dao) {
        this.dao = dao;
    }

    public void insert(Customer obj) throws SQLException {
        validarNome(obj);
        obj.setSituacaoFiado(CustomerStatus.DISPONIVEL);
        dao.insert(obj);
    }

    /** Nome obrigatório, sem espaços nas pontas e dentro do tamanho do banco. */
    static void validarNome(Customer obj) {
        String nome = obj.getNomeCliente() == null ? "" : obj.getNomeCliente().trim();
        if (nome.isEmpty()) throw new DbException("Digite o nome do cliente.");
        if (nome.length() > Customer.TAMANHO_MAX_NOME) {
            throw new DbException("O nome pode ter no máximo " + Customer.TAMANHO_MAX_NOME + " letras.");
        }
        obj.setNomeCliente(nome);
    }

    public void saveOrUpdate(Customer obj) {
        validarNome(obj);
        if (obj.getIdCliente() == 0) {
            try {
                insert(obj);
            } catch (SQLException e) {
                throw new DbException(e.getMessage(), e);
            }
        } else {
            BigDecimal devendo = Money.of(obj.getTotalDevendo());

            if (obj.getLimiteCredito().compareTo(devendo) < 0) {
                throw new DbException(String.format(
                        "O limite de crédito (%s) não pode ser menor do que o total devendo atual (%s)!",
                        Money.format(obj.getLimiteCredito()), Money.format(devendo)
                ));
            }
            dao.update(obj);
        }
    }

    /**
     * Só exclui clientes sem compras. Com compras, excluir apagaria o histórico de fiado
     * (o banco nem deixa, por causa da ligação com os pedidos); o caminho certo é bloquear.
     */
    public void deleteById(Integer id){
        if (dao.countOrders(id) > 0) {
            throw new DbException("Este cliente tem compras registradas e não pode ser excluído, "
                    + "para não perder o histórico de fiado.\n\n"
                    + "Para que ele não compre mais fiado, clique em Editar e mude a situação para BLOQUEADO.");
        }
        dao.deleteById(id);
    }


    public List<Customer> findAll() {
        return dao.findAll();
    }
}