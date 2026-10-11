-- V7: em qual caixa cada pagamento entrou (vendas e recebimentos de fiado).
-- Pagamentos de antes do caixa existir ficam com id_caixa NULL.
-- Um único ALTER: no MySQL, ou as duas partes são aplicadas ou nenhuma.

ALTER TABLE pagamento
    ADD COLUMN id_caixa INT NULL,
    ADD CONSTRAINT fk_pagamento_caixa FOREIGN KEY (id_caixa) REFERENCES caixa (id_caixa);
