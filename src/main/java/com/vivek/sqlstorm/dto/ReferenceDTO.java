/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.dto;

import org.json.JSONObject;

/**
 *
 * @author Vivek
 */
public class ReferenceDTO {
    public static enum Source{
        DB,
        CUSTOM
    }
    private String databaseName;
    private String tableName;
    private String columnName;
    private String referenceDatabaseName;
    private String referenceTableName;
    private String referenceColumnName;
    private JSONObject conditions;
    private Source source;

    /**
     * @return the tableName
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * @param tableName the tableName to set
     */
    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    /**
     * @return the columnName
     */
    public String getColumnName()
    {
        return columnName;
    }

    /**
     * @param columnName the columnName to set
     */
    public void setColumnName(String columnName)
    {
        this.columnName = columnName;
    }

    /**
     * @return the referenceTableName
     */
    public String getReferenceTableName()
    {
        return referenceTableName;
    }

    /**
     * @param referenceTableName the referenceTableName to set
     */
    public void setReferenceTableName(String referenceTableName)
    {
        this.referenceTableName = referenceTableName;
    }

    /**
     * @return the referenceColumnName
     */
    public String getReferenceColumnName()
    {
        return referenceColumnName;
    }

    /**
     * @param referenceColumnName the referenceColumnName to set
     */
    public void setReferenceColumnName(String referenceColumnName)
    {
        this.referenceColumnName = referenceColumnName;
    }

    public JSONObject getConditions() {
        return conditions;
    }

    public void setConditions(JSONObject conditions) {
        this.conditions = conditions;
    }

    public String getReferenceDatabaseName() {
        return referenceDatabaseName;
    }

    public void setReferenceDatabaseName(String referenceDatabaseName) {
        this.referenceDatabaseName = referenceDatabaseName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return "ReferenceDTO{" + "databaseName=" + databaseName + ", tableName=" + tableName + ", columnName=" + columnName + ", referenceDatabaseName=" + referenceDatabaseName + ", referenceTableName=" + referenceTableName + ", referenceColumnName=" + referenceColumnName + ", conditions=" + conditions + ", source=" + source + '}';
    }
   
}
