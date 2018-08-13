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
package com.vivek.sqlstorm.dto;

import com.vivek.utils.CommonFunctions;
import org.json.JSONObject;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class ColumnPath {
    private String database;
    private String table;
    private String column;
    private JSONObject conditions;
    private ReferenceDTO.Source source;

    public ColumnPath(String database, String table, String column) {
        this.database = database;
        this.table = table;
        this.column = column;
    }

    public ReferenceDTO.Source getSource() {
        return source;
    }

    public void setSource(ReferenceDTO.Source source) {
        this.source = source;
    }

    public JSONObject getConditions() {
        return conditions;
    }

    public void setConditions(JSONObject conditions) {
        this.conditions = conditions;
    }

    public String getDatabase() {
        return database;
    }

    public String getTable() {
        return table;
    }

    public String getColumn() {
        return column;
    }
    
    public String getPathString(){
        return CommonFunctions.getName(database, table, column);
    }
}
