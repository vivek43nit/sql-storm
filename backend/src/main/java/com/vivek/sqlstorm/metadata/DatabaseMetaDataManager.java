/*
 * The MIT License
 *
 * Copyright 2018 Vivek Kumar <vivek43nit@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.vivek.sqlstorm.metadata;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.DatabaseConfig;
import com.vivek.sqlstorm.config.loader.ConfigLoaderStrategy;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.ColumnPath;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.IndexInfo;
import com.vivek.sqlstorm.dto.MappingTableDto;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.sqlstorm.utils.DBHelper;
import com.vivek.utils.MultiMap;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
@Service
public class DatabaseMetaDataManager {
    private static final Logger logger = Logger.getLogger(DatabaseMetaDataManager.class);

    private volatile CustomRelationConfig config;
    private final DatabaseConnectionManager connectionManager;

    /**
     * Immutable snapshot of group → database → DatabaseDTO.
     * Readers call snapshotRef.get() with no lock — O(1), always consistent.
     * Writers build a full new map locally, then swap atomically.
     * In-flight readers continue using their reference to the old map safely.
     */
    private final AtomicReference<Map<String, Map<String, DatabaseDTO>>> snapshotRef
            = new AtomicReference<>(Collections.emptyMap());

    public DatabaseMetaDataManager(DatabaseConnectionManager connectionManager,
                                   ConfigLoaderStrategy<CustomRelationConfig> customMappingConfigLoader) {
        this.connectionManager = connectionManager;
        this.config = customMappingConfigLoader.load();
        logger.debug("Loaded Config : " + config.toString());
        snapshotRef.set(buildSnapshot(this.config));
    }

    /**
     * Hot-reload: called by the config loader's change listener when a new custom mapping
     * config is detected. Builds a new snapshot and atomically swaps it in — in-flight
     * readers continue with their reference to the old snapshot unaffected.
     */
    public synchronized void reloadCustomRelationConfig(CustomRelationConfig newConfig) {
        logger.info("Reloading custom relation config");
        this.config = newConfig;
        snapshotRef.set(buildSnapshot(newConfig));
    }

    public Set<String> getGroupNames() {
        return connectionManager.getGroupNames();
    }

    public Set<String> getDbNames(String groupName) throws ConnectionDetailNotFound {
        return connectionManager.getDbNames(groupName);
    }

    public Collection<TableDTO> getTables(String group, String database)
            throws ConnectionDetailNotFound, SQLException {
        DatabaseDTO dbmeta = getMetaData(group, database);
        Collection<TableDTO> tableCollection = dbmeta.getTables();

        List<TableDTO> tables;
        if (tableCollection instanceof List) {
            tables = (List<TableDTO>) tableCollection;
        } else {
            tables = new ArrayList<>(tableCollection);
        }

        tables.sort(Comparator.comparingInt(TableDTO::getWeight).reversed());
        return tables;
    }

    private void lazyLoadFromDb(DatabaseDTO dbmeta)
            throws SQLException, ConnectionDetailNotFound {
        // Connection is borrowed from the HikariCP pool; try-with-resources returns it.
        try (Connection con = connectionManager.getConnection(dbmeta.getGroup(), dbmeta.getName())) {
            List<TableDTO> dbtables = DBHelper.getTables(con);

            for (TableDTO dbtable : dbtables) {
                TableDTO t = dbmeta.getTableMetaData(dbtable.getTableName());
                if (t == null) {
                    dbmeta.addTableMetaData(dbtable);
                    t = dbtable;
                } else {
                    t.setRemark(dbtable.getRemark());
                }

                List<ColumnDTO> db_columns = DBHelper.getColumns(con, dbtable.getTableName());
                List<String> colNames = new ArrayList<>();

                for (ColumnDTO dbcolumn : db_columns) {
                    colNames.add(dbcolumn.getName());
                    ColumnDTO c = t.getColumnMetaData(dbcolumn.getName());
                    if (c == null) {
                        t.setColumnMetaData(dbcolumn);
                        c = dbcolumn;
                    } else {
                        c.setDataType(dbcolumn.getDataType());
                        c.setDescription(dbcolumn.getDescription());
                        c.setNullable(dbcolumn.getNullable());
                        c.setSize(dbcolumn.getSize());
                    }
                }
                t.setColumnNamesInDbOrder(colNames);

                List<IndexInfo> indexList = DBHelper.getAllIndexedColumns(con, dbtable.getTableName());
                logger.debug("Indexes for Table " + dbtable.getTableName() + " : " + indexList);
                t.setIndexingInfo(indexList);
            }

            Map<String, Map<String, DatabaseDTO>> snapshot = snapshotRef.get();
            for (TableDTO dbtable : dbtables) {
                List<ReferenceDTO> relations = DBHelper.getAllForeignKeys(con, dbtable.getTableName());
                logger.debug("Relations for Table " + dbtable.getTableName() + " : " + relations);

                for (ReferenceDTO relation : relations) {
                    ColumnPath referTo = new ColumnPath(relation.getReferenceDatabaseName(),
                            relation.getReferenceTableName(), relation.getReferenceColumnName());
                    ColumnPath referedBy = new ColumnPath(relation.getDatabaseName(),
                            relation.getTableName(), relation.getColumnName());
                    referTo.setConditions(relation.getConditions());
                    referTo.setSource(ReferenceDTO.Source.DB);
                    referedBy.setConditions(relation.getConditions());
                    referedBy.setSource(ReferenceDTO.Source.DB);

                    Map<String, DatabaseDTO> refToGroup = snapshot.get(dbmeta.getGroup());
                    if (refToGroup != null) {
                        DatabaseDTO refToDb = refToGroup.get(referTo.getDatabase());
                        if (refToDb != null) {
                            refToDb.getOrAddTableMetaData(referTo.getTable())
                                    .getOrAddColumnMetaData(referTo.getColumn())
                                    .addReferencedBy(referedBy);
                        }
                    }

                    try {
                        getWithoutCheckMetaData(dbmeta.getGroup(), referedBy.getDatabase())
                                .getOrAddTableMetaData(referedBy.getTable())
                                .getOrAddColumnMetaData(referedBy.getColumn())
                                .addReferTo(referTo);
                    } catch (ConnectionDetailNotFound ex) {
                        logger.warn("DB Config missing for :" + dbmeta);
                    }
                }
            }
        }

        // Volatile write — happens-before any subsequent volatile read of isLoadedFromDb(),
        // publishing all preceding table/column/FK state to all reader threads.
        dbmeta.setLoadedFromDb(true);
    }

    /**
     * Builds a complete new metadata snapshot from the connection config and custom relations.
     * Called on startup and on every config hot-reload. Never mutates the existing snapshot.
     */
    private Map<String, Map<String, DatabaseDTO>> buildSnapshot(CustomRelationConfig cfg) {
        Map<String, Map<String, DatabaseDTO>> newMap = new HashMap<>();

        // Tmp index: database name → groups that contain it
        MultiMap<String, String> databaseToGroup = new MultiMap<>();

        Set<String> groups = connectionManager.getGroupNames();
        for (String group : groups) {
            try {
                Set<String> databases = connectionManager.getDbNames(group);
                for (String database : databases) {
                    newMap.computeIfAbsent(group, k -> new HashMap<>())
                          .put(database, new DatabaseDTO(group, database));
                    databaseToGroup.put(database, group);
                }
            } catch (ConnectionDetailNotFound ex) {
                // never possible
            }
        }

        // Apply custom relations
        for (Map.Entry<String, DatabaseConfig> entry : cfg.getDatabases().entrySet()) {
            String databaseName = entry.getKey();
            DatabaseConfig dbConfig = entry.getValue();

            if (!databaseToGroup.containsKey(databaseName)) {
                logger.warn("database connection definition not found. Dropping: " + databaseName);
                continue;
            }

            for (Map.Entry<String, List<String>> tables : dbConfig.getAutoResolve().entrySet()) {
                String tableName = tables.getKey();
                for (String group : databaseToGroup.get(databaseName)) {
                    newMap.get(group).get(databaseName)
                          .getOrAddTableMetaData(tableName)
                          .setAutoResolveColumns(tables.getValue());
                }
            }

            for (Map.Entry<String, MappingTableDto> tables : dbConfig.getJointTables().entrySet()) {
                String tableName = tables.getKey();
                for (String group : databaseToGroup.get(databaseName)) {
                    newMap.get(group).get(databaseName)
                          .getOrAddTableMetaData(tableName)
                          .setJointTableMapping(tables.getValue());
                }
            }

            for (ReferenceDTO relation : dbConfig.getRelations()) {
                ColumnPath referTo = new ColumnPath(relation.getReferenceDatabaseName(),
                        relation.getReferenceTableName(), relation.getReferenceColumnName());
                ColumnPath referedBy = new ColumnPath(relation.getDatabaseName(),
                        relation.getTableName(), relation.getColumnName());

                referTo.setConditions(relation.getConditions());
                referTo.setSource(ReferenceDTO.Source.CUSTOM);
                referedBy.setConditions(relation.getConditions());
                referedBy.setSource(ReferenceDTO.Source.CUSTOM);

                for (String group : databaseToGroup.get(databaseName)) {
                    Map<String, DatabaseDTO> groupMap = newMap.get(group);
                    DatabaseDTO referToDb = groupMap.get(referTo.getDatabase());
                    if (referToDb != null) {
                        referToDb.getOrAddTableMetaData(referTo.getTable())
                                 .getOrAddColumnMetaData(referTo.getColumn())
                                 .addReferencedBy(referedBy);
                    }
                    if (databaseToGroup.containsKey(referedBy.getDatabase())) {
                        DatabaseDTO referedByDb = groupMap.get(referedBy.getDatabase());
                        if (referedByDb != null) {
                            referedByDb.getOrAddTableMetaData(referedBy.getTable())
                                       .getOrAddColumnMetaData(referedBy.getColumn())
                                       .addReferTo(referTo);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableMap(newMap);
    }

    public DatabaseDTO getWithoutCheckMetaData(String group, String database)
            throws ConnectionDetailNotFound {
        Map<String, Map<String, DatabaseDTO>> snapshot = snapshotRef.get();
        Map<String, DatabaseDTO> databases = snapshot.get(group);
        if (databases == null) {
            throw new ConnectionDetailNotFound("Invalid group name :" + group);
        }
        DatabaseDTO info = databases.get(database);
        if (info == null) {
            throw new ConnectionDetailNotFound("No database " + database + " in the group :" + group);
        }
        return info;
    }

    public DatabaseDTO getMetaData(String group, String database)
            throws ConnectionDetailNotFound, SQLException {
        DatabaseDTO dbmeta = getWithoutCheckMetaData(group, database);
        if (!dbmeta.isLoadedFromDb()) {              // fast path — volatile read, no lock
            synchronized (dbmeta) {                  // per-database lock, not global
                if (!dbmeta.isLoadedFromDb()) {      // double-check under lock
                    lazyLoadFromDb(dbmeta);
                }
            }
        }
        return dbmeta;
    }

    public CustomRelationConfig getCustomRelationConfig() {
        return config;
    }
}
