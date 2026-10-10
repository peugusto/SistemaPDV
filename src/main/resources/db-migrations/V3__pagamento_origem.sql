-- V3: de onde veio cada pagamento.
--   VENDA              -> pago na hora da compra (na tela de pagamento do PDV)
--   RECEBIMENTO_FIADO  -> pago depois, abatendo dívida de fiado
-- Permite mostrar na conta do cliente o que foi pago na compra e o que foi pago depois,
-- e vai ser usado pelo fechamento de caixa.

ALTER TABLE pagamento
    ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'VENDA';
