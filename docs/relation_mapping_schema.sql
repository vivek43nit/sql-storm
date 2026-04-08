-- relation_mapping table schema
-- One row per custom FK relation.
-- Used by RelationRowDbLoader when fkblitz.config.custom-mapping.source=relation-table
--
-- Change detection: SELECT MAX(updated_at) FROM relation_mapping
--   idx_updated_at makes this an O(1) index-only scan.
--
-- Soft-delete: set is_active = 0 to remove a relation.
--   ON UPDATE CURRENT_TIMESTAMP bumps updated_at, so the next poll detects the change.
--
-- Cross-database relations: ref_database_name is stored explicitly per row.

-- MySQL / MariaDB
CREATE TABLE relation_mapping (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    database_name     VARCHAR(128)    NOT NULL,
    table_name        VARCHAR(128)    NOT NULL,
    column_name       VARCHAR(128)    NOT NULL,
    ref_database_name VARCHAR(128)    NOT NULL,
    ref_table_name    VARCHAR(128)    NOT NULL,
    ref_column_name   VARCHAR(128)    NOT NULL,
    conditions_json   TEXT            NULL,        -- optional JSONObject, e.g. {"type":"inner"}
    is_active         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_relation (
        database_name, table_name, column_name,
        ref_database_name, ref_table_name, ref_column_name
    ),
    INDEX idx_updated_at (updated_at),          -- O(1) MAX(updated_at) scan
    INDEX idx_db_active  (database_name, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- PostgreSQL equivalent
-- (Replace TINYINT(1) with BOOLEAN, AUTO_INCREMENT with SERIAL,
--  and add a trigger for ON UPDATE behaviour)
--
-- CREATE TABLE relation_mapping (
--     id                BIGSERIAL       PRIMARY KEY,
--     database_name     VARCHAR(128)    NOT NULL,
--     table_name        VARCHAR(128)    NOT NULL,
--     column_name       VARCHAR(128)    NOT NULL,
--     ref_database_name VARCHAR(128)    NOT NULL,
--     ref_table_name    VARCHAR(128)    NOT NULL,
--     ref_column_name   VARCHAR(128)    NOT NULL,
--     conditions_json   TEXT            NULL,
--     is_active         BOOLEAN         NOT NULL DEFAULT TRUE,
--     created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
--     updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
--     CONSTRAINT uq_relation UNIQUE (
--         database_name, table_name, column_name,
--         ref_database_name, ref_table_name, ref_column_name
--     )
-- );
-- CREATE INDEX idx_updated_at ON relation_mapping (updated_at);
-- CREATE INDEX idx_db_active  ON relation_mapping (database_name, is_active);
--
-- CREATE OR REPLACE FUNCTION set_updated_at()
-- RETURNS TRIGGER AS $$
-- BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
-- $$ LANGUAGE plpgsql;
--
-- CREATE TRIGGER trg_relation_mapping_updated_at
-- BEFORE UPDATE ON relation_mapping
-- FOR EACH ROW EXECUTE FUNCTION set_updated_at();
