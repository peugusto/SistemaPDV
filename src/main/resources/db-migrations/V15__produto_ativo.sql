-- V15: produto ativo/inativo.
-- Produto que já foi vendido não pode ser excluído (o histórico de vendas precisa dele).
-- Em vez disso ele é desativado: some da venda, da busca e da lista de compras, mas o histórico continua.
ALTER TABLE produto
    ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
