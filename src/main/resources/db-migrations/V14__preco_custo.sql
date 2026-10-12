-- V14: preço de custo e lucro.

-- Último custo pago pelo produto (por unidade; no pesável, por kg). NULL = ainda não informado.
ALTER TABLE produto
    ADD COLUMN preco_custo DECIMAL(10,2) NULL;

-- Custo de cada item no momento da venda. Fica gravado na venda para o lucro do passado
-- não mudar quando o custo do produto mudar. NULL = vendido antes de ter custo (não entra no lucro).
ALTER TABLE itempedido
    ADD COLUMN custo_unitario DECIMAL(10,2) NULL;
