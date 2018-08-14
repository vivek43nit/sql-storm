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
package com.vivek.sqlstorm.dto.request;

import com.vivek.sqlstorm.constants.Constants;
import org.json.JSONObject;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class GetRelationsRequest {
    private String database;
    private String table;
    private String column;
    private JSONObject data;
    private Boolean append;
    private Boolean includeSelf;
    private int refRowLimit = Constants.DEFAULT_REFERENCES_ROWS_LIMIT;

    
    public boolean isValid(){
        return database != null && table != null && column != null;
    }

    public int getRefRowLimit() {
        return refRowLimit;
    }

    public void setRefRowLimit(int refRowLimit) {
        this.refRowLimit = refRowLimit;
    }

    

    
    public Boolean getIncludeSelf() {
        return includeSelf;
    }

    public void setIncludeSelf(Boolean includeSelf) {
        this.includeSelf = includeSelf;
    }

    public void setAppend(Boolean append) {
        this.append = append;
    }

    public Boolean getAppend() {
        return append;
    }

    public boolean isAppend() {
        return (append != null)?append:false;
    }
    
    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "GetRelationsRequest{" + "database=" + database + ", table=" + table + ", column=" + column + ", data=" + data + ", append=" + append + ", includeSelf=" + includeSelf + '}';
    }
    
    
}
