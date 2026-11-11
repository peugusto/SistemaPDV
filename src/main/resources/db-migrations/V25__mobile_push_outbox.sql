-- Sem backfill: apenas operacoes confirmadas depois desta migracao geram eventos.
CREATE TABLE push_event (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 source_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 kind VARCHAR(10) NOT NULL,
 target_device CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(6) NOT NULL,
 processed BOOLEAN NOT NULL DEFAULT FALSE,
 INDEX ix_push_event_pending(processed, created_at)
) ENGINE=InnoDB;
CREATE TABLE push_device (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id BIGINT NOT NULL,
 expo_token VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 registered_at DATETIME(6) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 last_test_at DATETIME(6) NULL,
 FOREIGN KEY (user_id) REFERENCES app_user(id),
 INDEX ix_push_device_owner(user_id, active)
) ENGINE=InnoDB;
CREATE TABLE push_delivery (
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 device_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt DATETIME(6) NOT NULL,
 lease_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 ticket_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(50) NULL,
 PRIMARY KEY(event_id, device_id),
 FOREIGN KEY(event_id) REFERENCES push_event(id),
 FOREIGN KEY(device_id) REFERENCES push_device(id),
 INDEX ix_push_delivery_due(state, next_attempt)
) ENGINE=InnoDB;

