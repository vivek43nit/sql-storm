package com.vivek.controller;

import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.apache.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/row")
public class RowMutationController {

    private static final Logger logger = Logger.getLogger(RowMutationController.class);

    private final DatabaseManager databaseManager;

    public RowMutationController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @PostMapping("/add")
    public ResponseEntity<?> addRow(@RequestParam String group,
                                     @RequestParam String database,
                                     @RequestParam String table,
                                     @RequestBody Map<String, Object> data) {
        try {
            if (!databaseManager.isUpdatableConnection(group, database)) {
                return ResponseEntity.status(403).body("Update permission is prohibited for this database");
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
            return ResponseEntity.ok("Row added successfully");
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            logger.error("Add row error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

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
            return ResponseEntity.ok("Row updated successfully");
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            logger.error("Edit row error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

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
            return ResponseEntity.ok("Row deleted successfully");
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            logger.error("Delete row error: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
