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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class TableDTO {
    
    private String tableName;
    private String remark;
    private List<String> autoResolveColumns;
    private List<String> columnNamesInDbOrder;
    private Map<String, ColumnDTO> columns;
    private String primaryKey;
    
    private MappingTableDto jointTableMapping;
    
    public TableDTO(String tableName) {
        this.tableName = tableName;
    }

    public TableDTO(String tableName, String remark) {
        this.tableName = tableName;
        this.remark = remark;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }
    
    public List<String> getColumnNamesInDbOrder() {
        return columnNamesInDbOrder;
    }

    public void setColumnNamesInDbOrder(List<String> columnNamesInDbOrder) {
        this.columnNamesInDbOrder = columnNamesInDbOrder;
    }

    public List<String> getAutoResolveColumns() {
        return autoResolveColumns;
    }

    public void setAutoResolveColumns(List<String> autoResolveColumns) {
        this.autoResolveColumns = autoResolveColumns;
    }

    public MappingTableDto getJointTableMapping() {
        return jointTableMapping;
    }

    public void setJointTableMapping(MappingTableDto jointTableMapping) {
        this.jointTableMapping = jointTableMapping;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Collection<ColumnDTO> getColumns() {
        return columns.values();
    }

    
    public ColumnDTO getColumnMetaData(String name) {
        return (columns == null)? null : columns.get(name);
    }

    public void setColumnMetaData(ColumnDTO column) {
        if(this.columns == null){
            this.columns = new HashMap<String, ColumnDTO>();
        }
        this.columns.put(column.getName(), column);
    }
    
    public ColumnDTO getOrAddColumnMetaData(String name) {
        if(columns == null){
            columns = new HashMap<String, ColumnDTO>();
            columns.put(name, new ColumnDTO(name));
        }else if(!columns.containsKey(name)){
            columns.put(name, new ColumnDTO(name));
        }
        return columns.get(name);
    }
    
    public int getWeight(){
        int weight = 0;
        for(ColumnDTO col : columns.values()){
            weight += col.getReferTo().size();
            weight += col.getReferencedBy().size();
        }
        return weight;
    }
    
    public void setIndexingInfo(List<IndexInfo> indexes){
        for(IndexInfo index : indexes){
            ColumnDTO col = this.columns.get(index.getColumn());
            col.setIndexed(true);
            col.setPrimaryKey(index.isPrimaryKey());
            col.setUnique(index.isUnique());
            if(index.isPrimaryKey()){
                this.primaryKey = col.getName();
            }
        }
    }
}
