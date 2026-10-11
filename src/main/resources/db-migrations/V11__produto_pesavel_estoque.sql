-- V11: produtos pesáveis e produtos sem controle de estoque.
-- tipo_venda: UNIDADE (como sempre foi) ou PESAVEL (o caixa digita o valor mostrado na balança).
-- controla_estoque: FALSE = a venda não confere nem baixa o estoque (pão, açaí, frios...
--   produtos com retiradas que ninguém registra). Pesável é sempre gravado como FALSE.
-- Produtos já cadastrados continuam como antes: UNIDADE e controlando estoque.

ALTER TABLE produto
    ADD COLUMN tipo_venda       VARCHAR(10) NOT NULL DEFAULT 'UNIDADE',
    ADD COLUMN controla_estoque BOOLEAN     NOT NULL DEFAULT TRUE;
