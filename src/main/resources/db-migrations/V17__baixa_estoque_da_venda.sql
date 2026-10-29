-- Guarda o efeito efetivo de cada venda no estoque. NULL = histórico anterior não verificável.
-- Não inferir baixas antigas pela configuração atual do produto.
ALTER TABLE itempedido ADD COLUMN estoque_baixado INT NULL DEFAULT NULL;