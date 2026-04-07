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
package com.vivek.sqlstorm.config.customrelation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomRelationConfig {
    Map<String, DatabaseConfig> databases;

    /**
     * Columns whose values are masked for users without SENSITIVE_DATA_RO permission.
     * Defined in custom_mapping.json under "sensitiveColumns".
     * Wildcard "*" for database matches any database.
     */
    private List<SensitiveColumn> sensitiveColumns = new ArrayList<>();

    public CustomRelationConfig(Map<String, DatabaseConfig> databases) {
        this.databases = databases;
    }

    @Data
    public static class SensitiveColumn {
        /** Database name, or "*" to match all databases. */
        private String database;
        private String table;
        private String column;
    }

    /**
     * Returns true if the given database.table.column is marked as sensitive.
     */
    public boolean isSensitive(String database, String table, String column) {
        for (SensitiveColumn sc : sensitiveColumns) {
            boolean dbMatch = "*".equals(sc.getDatabase()) || sc.getDatabase().equals(database);
            if (dbMatch && sc.getTable().equals(table) && sc.getColumn().equals(column)) {
                return true;
            }
        }
        return false;
    }
}
