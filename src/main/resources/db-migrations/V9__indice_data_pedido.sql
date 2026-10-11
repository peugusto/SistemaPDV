-- V9: índice na data do pedido.
-- O histórico de vendas filtra sempre por período; sem índice, o MySQL lê todos os pedidos
-- já feitos a cada consulta. Com ele, vai direto ao intervalo pedido, mesmo depois de anos de vendas.

CREATE INDEX idx_pedido_data ON pedido (data_pedido);
