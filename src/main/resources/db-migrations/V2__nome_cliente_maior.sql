-- V2: nome do cliente de 20 para 100 caracteres.
-- A tela de cadastro aceitava 30 e a de edição não tinha limite, mas o banco só guardava 20:
-- nomes como "Maria Aparecida Santos" davam erro ao salvar.
-- O limite das telas acompanha este valor (Customer.TAMANHO_MAX_NOME).

ALTER TABLE cliente MODIFY nome_cliente VARCHAR(100) NOT NULL;
