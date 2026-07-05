-- Initial migration.
-- No business logic yet; this table simply confirms that Flyway runs on startup.
CREATE TABLE IF NOT EXISTS app_info (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_info (name) VALUES ('rke-backend');
