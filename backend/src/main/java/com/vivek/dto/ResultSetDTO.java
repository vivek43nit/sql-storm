package com.vivek.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ResultSetDTO {
    private String query;
    private String info;
    private String relation;   // self / referTo / referencedBy
    private String group;
    private String database;
    private String table;
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private boolean updatable;
    private boolean deletable;

    // FK metadata: column name → list of "db.table.column" paths
    // referToColumns: this column's value points TO another table (follow the FK)
    private Map<String, List<String>> referToColumns;
    // referencedByColumns: other tables reference this column (show child rows)
    private Map<String, List<String>> referencedByColumns;

    private String pk;
}
