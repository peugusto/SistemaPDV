-- Contas individuais dos proprietarios para acesso ao aplicativo/painel.
-- Separadas de cliente, do PIN local do PDV e das contas tecnicas do MySQL.
-- As senhas devem ser gravadas pela API como hash adaptativo, nunca em texto puro.
-- Esta migracao nao cria contas nem senhas padrao.
CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
