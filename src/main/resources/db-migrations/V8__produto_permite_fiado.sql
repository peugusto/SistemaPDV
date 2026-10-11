-- V8: se o produto pode ser vendido no fiado (ex.: cigarro e bebida alcoólica não podem).
-- Produtos já cadastrados continuam podendo; o bloqueio é marcado no cadastro do produto.

ALTER TABLE produto
    ADD COLUMN permite_fiado BOOLEAN NOT NULL DEFAULT TRUE;
