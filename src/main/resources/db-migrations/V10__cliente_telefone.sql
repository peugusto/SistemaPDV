-- V10: celular/WhatsApp do cliente.
-- A tela de cadastro já tinha o campo, mas ele não era salvo. Guarda só os números
-- (ex.: 11987654321); a tela mostra formatado: (11) 98765-4321. Opcional (NULL).

ALTER TABLE cliente ADD COLUMN telefone VARCHAR(20) NULL;
