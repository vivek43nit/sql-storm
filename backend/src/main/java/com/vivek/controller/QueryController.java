package com.vivek.controller;

import com.vivek.dto.ResultSetDTO;
import com.vivek.metrics.FkBlitzMetrics;
import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.constants.Constants;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.ColumnPath;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.dto.request.ExecuteRequest;
import com.vivek.sqlstorm.dto.request.GetRelationsRequest;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.sqlstorm.utils.DBHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Query", description = "Execute SQL queries and navigate FK relationships")
public class QueryController {

    private static final Logger logger = Logger.getLogger(QueryController.class);

    private final DatabaseManager databaseManager;
    private final FkBlitzMetrics metrics;

    public QueryController(DatabaseManager databaseManager, FkBlitzMetrics metrics) {
        this.databaseManager = databaseManager;
        this.metrics = metrics;
    }

    @Operation(summary = "Execute a SQL query", description = "Runs a SELECT or DML statement against the specified database. Rate-limited to 60 req/min per user.")
    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestParam String group,
                                     @RequestBody ExecuteRequest req) {
        if (!req.isValid()) {
            return ResponseEntity.badRequest().body("Invalid request");
        }
        long start = System.currentTimeMillis();
        try {
            boolean updatable = databaseManager.isUpdatableConnection(group, req.getDatabase());
            boolean deletable = databaseManager.isDeletableConnection(group, req.getDatabase());

            if (!req.getQueryType().equals("S")) {
                if (!updatable) {
                    return ResponseEntity.status(403).body("Update permission is prohibited for this database");
                }
                Connection con = databaseManager.getConnection(group, req.getDatabase());
                try (PreparedStatement ps = con.prepareStatement(req.getQuery())) {
                    int count = ps.executeUpdate();
                    return ResponseEntity.ok(Map.of("affectedRows", count));
                }
            }

            Connection con = databaseManager.getConnection(group, req.getDatabase());
            ResultSetDTO dto = executeSelectQuery(con, req.getQuery(), req.getInfo(),
                    req.getRelation(), group, req.getDatabase(), updatable, deletable);
            metrics.recordQuerySuccess(group, req.getDatabase(), System.currentTimeMillis() - start);
            return ResponseEntity.ok(dto);

        } catch (ConnectionDetailNotFound | SQLException e) {
            metrics.recordQueryError(group, req.getDatabase());
            logger.error("Execute error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @Operation(summary = "Get rows that reference this value", description = "Returns all rows in other tables whose FK column points to this column's value.")
    @GetMapping("/references")
    public ResponseEntity<?> getReferences(@RequestParam String group,
                                            @RequestParam String database,
                                            @RequestParam String table,
                                            @RequestParam String column,
                                            @RequestParam String row,
                                            @RequestParam(defaultValue = "false") boolean includeSelf,
                                            @RequestParam(defaultValue = "100") int refRowLimit) {
        try {
            JSONObject data = new JSONObject(row);
            String value = String.valueOf(data.get(column));

            TableDTO tableMetaData = databaseManager.getMetaData(group, database).getTableMetaData(table);
            ColumnDTO colMetaData = tableMetaData.getColumnMetaData(column);

            if (colMetaData.getReferencedBy().isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            ColumnPath selfPath = new ColumnPath(database, table, column);
            List<ColumnPath> referencedByList = new ArrayList<>();
            if (includeSelf) referencedByList.add(selfPath);
            referencedByList.addAll(colMetaData.getReferencedBy());

            List<ResultSetDTO> results = new ArrayList<>();
            boolean isAppend = false;
            for (ColumnPath referencedBy : referencedByList) {
                for (ExecuteRequest r : DBHelper.getExecuteRequestsForReferedByReq(
                        databaseManager, group, selfPath, referencedBy, value, isAppend, refRowLimit)) {
                    Connection con = databaseManager.getConnection(group, r.getDatabase());
                    boolean upd = databaseManager.isUpdatableConnection(group, r.getDatabase());
                    boolean del = databaseManager.isDeletableConnection(group, r.getDatabase());
                    results.add(executeSelectQuery(con, r.getQuery(), r.getInfo(), r.getRelation(),
                            group, r.getDatabase(), upd, del));
                    isAppend = true;
                }
            }
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            logger.error("References error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @Operation(summary = "Get rows this value references (FK look-up)", description = "Follows FK pointers from this column's value to the referenced table.")
    @GetMapping("/dereferences")
    public ResponseEntity<?> getDeReferences(@RequestParam String group,
                                              @RequestParam String database,
                                              @RequestParam String table,
                                              @RequestParam String column,
                                              @RequestParam String row,
                                              @RequestParam(defaultValue = "100") int refRowLimit) {
        try {
            JSONObject data = new JSONObject(row);
            String value = String.valueOf(data.get(column));

            ColumnDTO colMetaData = databaseManager.getMetaData(group, database)
                    .getTableMetaData(table).getColumnMetaData(column);

            if (colMetaData.getReferTo().isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<ResultSetDTO> results = new ArrayList<>();
            for (ColumnPath referTo : colMetaData.getReferTo()) {
                if (referTo.getConditions() != null && !DBHelper.isReferToConditionMatch(referTo.getConditions(), data)) {
                    continue;
                }
                TableDTO refTable = databaseManager.getMetaData(group, referTo.getDatabase())
                        .getTableMetaData(referTo.getTable());
                String query = String.format("select * from %s where %s='%s'",
                        referTo.getTable(), referTo.getColumn(), value);
                if (refTable.getPrimaryKey() != null) {
                    query += " order by " + refTable.getPrimaryKey() + " DESC";
                }
                query += " limit " + refRowLimit;

                Connection con = databaseManager.getConnection(group, referTo.getDatabase());
                boolean upd = databaseManager.isUpdatableConnection(group, referTo.getDatabase());
                boolean del = databaseManager.isDeletableConnection(group, referTo.getDatabase());
                String info = table + "." + column + " -> " + referTo.getTable() + "." + referTo.getColumn();
                results.add(executeSelectQuery(con, query, info, ExecuteRequest.REFER_TO,
                        group, referTo.getDatabase(), upd, del));
            }
            return ResponseEntity.ok(results);

        } catch (ConnectionDetailNotFound | SQLException e) {
            logger.error("DeReferences error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @Operation(summary = "Trace all FK connections for a row", description = "Combines references + dereferences to show the full FK graph for a given row.")
    @GetMapping("/trace")
    public ResponseEntity<?> traceRow(@RequestParam String group,
                                       @RequestParam String database,
                                       @RequestParam String table,
                                       @RequestParam String row,
                                       @RequestParam(defaultValue = "100") int refRowLimit) {
        try {
            JSONObject data = new JSONObject(row);
            TableDTO tableMetaData = databaseManager.getMetaData(group, database).getTableMetaData(table);

            List<ResultSetDTO> results = new ArrayList<>();

            // deReferences (columns that referTo something)
            for (ColumnDTO col : tableMetaData.getColumns()) {
                if (col.getReferTo().isEmpty()) continue;
                ResponseEntity<?> resp = getDeReferences(group, database, table, col.getName(), row, refRowLimit);
                if (resp.getBody() instanceof List) {
                    results.addAll((List<ResultSetDTO>) resp.getBody());
                }
            }

            // references (columns that are referencedBy something)
            boolean includeSelf = true;
            for (ColumnDTO col : tableMetaData.getColumns()) {
                if (col.getReferencedBy().isEmpty()) continue;
                ResponseEntity<?> resp = getReferences(group, database, table, col.getName(), row, includeSelf, refRowLimit);
                if (resp.getBody() instanceof List) {
                    results.addAll((List<ResultSetDTO>) resp.getBody());
                }
                includeSelf = false;
            }

            return ResponseEntity.ok(results);

        } catch (ConnectionDetailNotFound | SQLException e) {
            logger.error("Trace error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    private static final String MASKED = "\u2022\u2022\u2022\u2022\u2022\u2022";

    private boolean canViewSensitive() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "SENSITIVE_DATA_RO".equals(a.getAuthority())
                        || "SENSITIVE_DATA_RW".equals(a.getAuthority()));
    }

    private ResultSetDTO executeSelectQuery(Connection con, String query, String info, String relation,
                                             String group, String database,
                                             boolean updatable, boolean deletable) throws SQLException {
        ResultSetDTO dto = new ResultSetDTO();
        dto.setQuery(query);
        dto.setInfo(info);
        dto.setRelation(relation);
        dto.setGroup(group);
        dto.setDatabase(database);
        dto.setUpdatable(updatable);
        dto.setDeletable(deletable);

        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            dto.setColumns(columns);

            // Detect table from first column's table name
            if (colCount > 0) dto.setTable(meta.getTableName(1));

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    rowMap.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(rowMap);
            }
            dto.setRows(rows);
        }

        // Populate FK metadata so frontend knows which columns have navigable links
        enrichWithFkMetadata(dto, group, database);

        // Mask sensitive column values if user lacks SENSITIVE_DATA_RO
        if (!canViewSensitive() && dto.getTable() != null) {
            CustomRelationConfig customCfg = databaseManager.getCustomRelationConfig();
            if (customCfg != null) {
                for (Map<String, Object> row : dto.getRows()) {
                    for (String col : dto.getColumns()) {
                        if (customCfg.isSensitive(database, dto.getTable(), col)) {
                            row.put(col, MASKED);
                        }
                    }
                }
            }
        }

        return dto;
    }

    private void enrichWithFkMetadata(ResultSetDTO dto, String group, String database) {
        if (dto.getTable() == null || dto.getColumns() == null) return;
        try {
            com.vivek.sqlstorm.dto.DatabaseDTO dbMeta = databaseManager.getMetaData(group, database);
            com.vivek.sqlstorm.dto.TableDTO tableMeta = dbMeta.getTableMetaData(dto.getTable());
            if (tableMeta == null) return;

            Map<String, List<String>> referTo = new LinkedHashMap<>();
            Map<String, List<String>> referencedBy = new LinkedHashMap<>();

            for (String col : dto.getColumns()) {
                com.vivek.sqlstorm.dto.ColumnDTO colMeta = tableMeta.getColumnMetaData(col);
                if (colMeta == null) continue;

                if (!colMeta.getReferTo().isEmpty()) {
                    referTo.put(col, colMeta.getReferTo().stream()
                            .map(p -> p.getDatabase() + "." + p.getTable() + "." + p.getColumn())
                            .collect(java.util.stream.Collectors.toList()));
                }
                if (!colMeta.getReferencedBy().isEmpty()) {
                    referencedBy.put(col, colMeta.getReferencedBy().stream()
                            .map(p -> p.getDatabase() + "." + p.getTable() + "." + p.getColumn())
                            .collect(java.util.stream.Collectors.toList()));
                }
            }

            if (!referTo.isEmpty()) dto.setReferToColumns(referTo);
            if (!referencedBy.isEmpty()) dto.setReferencedByColumns(referencedBy);
            if (tableMeta.getPrimaryKey() != null) dto.setPk(tableMeta.getPrimaryKey());

        } catch (Exception e) {
            logger.warn("Could not enrich FK metadata for " + dto.getTable() + ": " + e.getMessage());
        }
    }
}
