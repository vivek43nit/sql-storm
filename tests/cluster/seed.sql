-- Cluster test seed: creates relation_mapping table used by RelationRowDbLoader
-- Mounted into MariaDB via docker-entrypoint-initdb.d/ — runs automatically on first start.

CREATE TABLE IF NOT EXISTS relation_mapping (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    database_name     VARCHAR(128)    NOT NULL,
    table_name        VARCHAR(128)    NOT NULL,
    column_name       VARCHAR(128)    NOT NULL,
    ref_database_name VARCHAR(128)    NOT NULL,
    ref_table_name    VARCHAR(128)    NOT NULL,
    ref_column_name   VARCHAR(128)    NOT NULL,
    conditions_json   TEXT            NULL,
    is_active         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                               ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_relation (
        database_name, table_name, column_name,
        ref_database_name, ref_table_name, ref_column_name
    ),
    INDEX idx_updated_at (updated_at),
    INDEX idx_db_active  (database_name, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
