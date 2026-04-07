package com.vivek.controller;

import com.vivek.metrics.FkBlitzMetrics;
import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/row")
@Tag(name = "Row Mutations", description = "Add, edit, and delete table rows. Requires READ_WRITE or ADMIN role. Rate-limited to 30 req/min per user.")
public class RowMutationController {

    private static final Logger logger = Logger.getLogger(RowMutationController.class);

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    private static boolean canWriteSensitive() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "SENSITIVE_DATA_RW".equals(a.getAuthority()));
    }

    private final DatabaseManager databaseManager;
    private final FkBlitzMetrics metrics;

    public RowMutationController(DatabaseManager databaseManager, FkBlitzMetrics metrics) {
        this.databaseManager = databaseManager;
        this.metrics = metrics;
    }

    @Operation(summary = "Insert a new row")
    @PostMapping("/add")
    public ResponseEntity<?> addRow(@RequestParam String group,
                                     @RequestParam String database,
                                     @RequestParam String table,
                                     @RequestBody Map<String, Object> data) {
        try {
            if (!databaseManager.isUpdatableConnection(group, database)) {
                return ResponseEntity.status(403).body("Update permission is prohibited for this database");
            }
            CustomRelationConfig customCfg = databaseManager.getCustomRelationConfig();
            if (customCfg != null && !canWriteSensitive()) {
                for (String col : data.keySet()) {
                    if (customCfg.isSensitive(database, table, col)) {
                        return ResponseEntity.status(403).body("Cannot write to sensitive column: " + col);
                    }
                }
            }
            Connection con = databaseManager.getConnection(group, database);

            StringJoiner cols = new StringJoiner(", ");
            StringJoiner placeholders = new StringJoiner(", ");
            data.keySet().forEach(k -> { cols.add("`" + k + "`"); placeholders.add("?"); });

            String sql = String.format("INSERT INTO `%s` (%s) VALUES (%s)", table, cols, placeholders);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int i = 1;
                for (Object val : data.values()) ps.setObject(i++, val);
                ps.executeUpdate();
            }
            logger.info("AUDIT ADD_ROW user='" + currentUser() + "' group=" + group + " db=" + database + " table=" + table);
            metrics.recordCrudOperation("add", table);
            return ResponseEntity.ok("Row added successfully");
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            logger.error("Add row error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @Operation(summary = "Update an existing row by primary key")
    @PutMapping("/edit")
    public ResponseEntity<?> editRow(@RequestParam String group,
                                      @RequestParam String database,
                                      @RequestParam String table,
                                      @RequestParam String pk,
                                      @RequestParam Object pkValue,
                                      @RequestBody Map<String, Object> data) {
        try {
            if (!databaseManager.isUpdatableConnection(group, database)) {
                return ResponseEntity.status(403).body("Update permission is prohibited for this database");
            }
            CustomRelationConfig customCfg = databaseManager.getCustomRelationConfig();
            if (customCfg != null && !canWriteSensitive()) {
                for (String col : data.keySet()) {
                    if (customCfg.isSensitive(database, table, col)) {
                        return ResponseEntity.status(403).body("Cannot write to sensitive column: " + col);
                    }
                }
            }
            Connection con = databaseManager.getConnection(group, database);

            StringJoiner setClauses = new StringJoiner(", ");
            data.keySet().forEach(k -> setClauses.add("`" + k + "` = ?"));

            String sql = String.format("UPDATE `%s` SET %s WHERE `%s` = ?", table, setClauses, pk);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int i = 1;
                for (Object val : data.values()) ps.setObject(i++, val);
                ps.setObject(i, pkValue);
                int affected = ps.executeUpdate();
                if (affected == 0) return ResponseEntity.badRequest().body("No rows updated");
            }
            logger.info("AUDIT EDIT_ROW user='" + currentUser() + "' group=" + group + " db=" + database + " table=" + table + " pk=" + pk + " pkValue=" + pkValue);
            metrics.recordCrudOperation("edit", table);
            return ResponseEntity.ok("Row updated successfully");
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            logger.error("Edit row error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a row by primary key")
    @DeleteMapping
    public ResponseEntity<?> deleteRow(@RequestParam String group,
                                        @RequestParam String database,
                                        @RequestParam String table,
                                        @RequestParam String pk,
                                        @RequestParam Object pkValue) {
        try {
            if (!databaseManager.isDeletableConnection(group, database)) {
                return ResponseEntity.status(403).body("Delete permission is prohibited for this database");
            }
            Connection con = databaseManager.getConnection(group, database);

            String sql = String.format("DELETE FROM `%s` WHERE `%s` = ?", table, pk);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setObject(1, pkValue);
                int affected = ps.executeUpdate();
                if (affected == 0) return ResponseEntity.badRequest().body("No rows deleted");
            }
            logger.info("AUDIT DELETE_ROW user='" + currentUser() + "' group=" + group + " db=" + database + " table=" + table + " pk=" + pk + " pkValue=" + pkValue);
            metrics.recordCrudOperation("delete", table);
            return ResponseEntity.ok("Row deleted successfully");
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            logger.error("Delete row error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
