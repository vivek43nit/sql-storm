package com.vivek.controller;

import com.vivek.dto.ResultSetDTO;
import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MetaDataController {

    private final DatabaseManager databaseManager;

    public MetaDataController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @GetMapping("/groups")
    public Set<String> getGroups() {
        return databaseManager.getGroupNames();
    }

    @GetMapping("/databases")
    public ResponseEntity<?> getDatabases(@RequestParam String group) {
        try {
            return ResponseEntity.ok(databaseManager.getDbNames(group));
        } catch (ConnectionDetailNotFound e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<?> getTables(@RequestParam String group, @RequestParam String database) {
        try {
            Collection<TableDTO> tables = databaseManager.getTables(group, database);
            List<Map<String, Object>> result = new ArrayList<>();
            for (TableDTO t : tables) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", t.getTableName());
                entry.put("remark", t.getRemark());
                entry.put("primaryKey", t.getPrimaryKey());
                result.add(entry);
            }
            return ResponseEntity.ok(result);
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/relations")
    public ResponseEntity<?> getRelations(
            @RequestParam String group,
            @RequestParam String database,
            @RequestParam(required = false) String table) {
        try {
            Collection<TableDTO> tables = databaseManager.getTables(group, database);
            List<Map<String, Object>> result = new ArrayList<>();
            for (TableDTO t : tables) {
                if (table != null && !t.getTableName().equals(table)) continue;
                for (ColumnDTO col : t.getColumns()) {
                    if (!col.getReferTo().isEmpty() || !col.getReferencedBy().isEmpty()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("table", t.getTableName());
                        entry.put("column", col.getName());
                        entry.put("referTo", col.getReferTo().stream()
                                .map(p -> p.getDatabase() + "." + p.getTable() + "." + p.getColumn())
                                .collect(Collectors.toList()));
                        entry.put("referencedBy", col.getReferencedBy().stream()
                                .map(p -> p.getDatabase() + "." + p.getTable() + "." + p.getColumn())
                                .collect(Collectors.toList()));
                        result.add(entry);
                    }
                }
            }
            return ResponseEntity.ok(result);
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/suggestions")
    public ResponseEntity<?> getSuggestions(@RequestParam String group) {
        try {
            Set<String> databases = databaseManager.getDbNames(group);
            Map<String, List<Map<String, Object>>> suggestions = new LinkedHashMap<>();
            for (String db : databases) {
                List<Map<String, Object>> dbSuggestions = new ArrayList<>();
                Collection<TableDTO> tables = databaseManager.getTables(group, db);
                for (TableDTO t : tables) {
                    for (ColumnDTO col : t.getColumns()) {
                        if (!col.getReferTo().isEmpty()) {
                            Map<String, Object> s = new LinkedHashMap<>();
                            s.put("table", t.getTableName());
                            s.put("column", col.getName());
                            s.put("referTo", col.getReferTo().stream()
                                    .map(p -> p.getDatabase() + "." + p.getTable() + "." + p.getColumn())
                                    .collect(Collectors.toList()));
                            dbSuggestions.add(s);
                        }
                    }
                }
                if (!dbSuggestions.isEmpty()) suggestions.put(db, dbSuggestions);
            }
            return ResponseEntity.ok(suggestions);
        } catch (ConnectionDetailNotFound | SQLException | ClassNotFoundException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
