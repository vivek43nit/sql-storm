-- Cluster test seed: creates tables for the cluster test database.
-- Mounted into MariaDB via docker-entrypoint-initdb.d/ — runs automatically on first start.

-- Tables that the custom relations reference (no FK constraints — the point is
-- that FkBlitz overlays virtual relations from relation_mapping on top of the metadata)
CREATE TABLE IF NOT EXISTS users (
    id   BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount  DECIMAL(10,2),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Connection config table: stores the DatabaseConnection.xml content as a DB blob.
-- FkBlitz nodes are configured with source=db to read connections from here,
-- avoiding static file mounts in multi-node cluster deployments.
CREATE TABLE IF NOT EXISTS fkblitz_connection_config (
    id             INT         NOT NULL AUTO_INCREMENT,
    config_content MEDIUMTEXT  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO fkblitz_connection_config (config_content) VALUES (
'<CONNECTIONS CONNECTION_EXPIRY_TIME="3600000" MAX_RETRY_COUNT="1">
    <CONNECTION ID="1" GROUP="cluster" DB_NAME="clustertest"
                USER_NAME="fkblitz" PASSWORD="fkblitz123"
                DRIVER_CLASS_NAME="org.mariadb.jdbc.Driver"
                DATABASE_URL="jdbc:mariadb://mariadb:3306/clustertest?useInformationSchema=true"
                UPDATABLE="true" DELETABLE="true"/>
</CONNECTIONS>'
);

-- Relation mapping table used by RelationRowDbLoader

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
