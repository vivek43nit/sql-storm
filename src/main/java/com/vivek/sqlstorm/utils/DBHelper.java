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
package com.vivek.sqlstorm.utils;

import com.vivek.sqlstorm.DatabaseManager;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.ColumnPath;
import com.vivek.sqlstorm.dto.IndexInfo;
import com.vivek.sqlstorm.dto.MappingTableDto;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.dto.request.ExecuteRequest;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.utils.parser.ConfigParsingError;
import com.vivek.utils.parser.NoParserRegistered;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class DBHelper {

    public static List<TableDTO> getTables(Connection con) throws SQLException {
        DatabaseMetaData dbMetaData = con.getMetaData();
        ResultSet rs = dbMetaData.getTables(con.getCatalog(), null, "%", null);
        List<TableDTO> list = new ArrayList<TableDTO>();
        while (rs.next()) {
            list.add(new TableDTO(rs.getString("TABLE_NAME"), rs.getString("REMARKS")));
        }
        return list;
    }

    public static List<ColumnDTO> getColumns(Connection con, String tableName) throws SQLException {
        DatabaseMetaData dbMetaData = con.getMetaData();
        ResultSet rs = dbMetaData.getColumns(con.getCatalog(), null, tableName, "%");

        List<ColumnDTO> list = new ArrayList<ColumnDTO>();
        while (rs.next()) {
            list.add(new ColumnDTO(rs.getString("COLUMN_NAME"), "", rs.getInt("DATA_TYPE"), rs.getInt("COLUMN_SIZE"), rs.getInt("NULLABLE")));
        }
        return list;
    }

    private static void createCsvFromResultSet(ResultSet rs, FileOutputStream fout) throws SQLException, IOException {
        boolean isFirst = true;
        int columnCount= -1;
        while (rs.next()){
            if(isFirst){
                isFirst = false;
                ResultSetMetaData resultSetMetaData =  rs.getMetaData();
                columnCount = resultSetMetaData.getColumnCount();
                for(int i=1; i <=resultSetMetaData.getColumnCount(); i++){
                    if(i!=1){
                        fout.write(",".getBytes());
                    }
                    fout.write(String.format("%s(%d)", resultSetMetaData.getColumnName(i), resultSetMetaData.getColumnType(i)).getBytes());
                }
                fout.write("\n".getBytes());
            }
            for(int i=1; i <= columnCount; i++){
                if(i!=1){
                    fout.write(",".getBytes());
                }
                fout.write(String.format("\"%s\"", rs.getString(i)).getBytes());
            }
            fout.write("\n".getBytes());
        }
    }


    
    public static List<IndexInfo> getAllIndexedColumns(Connection con, String tableName) throws SQLException {
        DatabaseMetaData dbMetaData = con.getMetaData();
        
        ResultSet rs = dbMetaData.getPrimaryKeys(con.getCatalog(), null, tableName);
        String primaryKeyColName = "";
        if(rs.next()){
            primaryKeyColName = rs.getString("COLUMN_NAME");
        }
        
        List<IndexInfo> list = new ArrayList<IndexInfo>();
        rs = dbMetaData.getIndexInfo(con.getCatalog(), null, tableName, false, false);
        while (rs.next()) {
            if(rs.getShort("ORDINAL_POSITION") > 1) continue;
            list.add(new IndexInfo(rs.getString("COLUMN_NAME"), rs.getString("COLUMN_NAME").equals(primaryKeyColName), !rs.getBoolean("NON_UNIQUE")));
        }
        return list;
    }

    public static List<ReferenceDTO> getAllForeignKeys(Connection con, String tableName) throws SQLException {
        DatabaseMetaData dbMetaData = con.getMetaData();
        ResultSet rs = dbMetaData.getImportedKeys(con.getCatalog(), null, tableName);
        List<ReferenceDTO> list = new ArrayList<ReferenceDTO>();
        while (rs.next()) {
            ReferenceDTO dto = new ReferenceDTO();
            dto.setDatabaseName(con.getCatalog());
            dto.setTableName(rs.getString("FKTABLE_NAME"));
            dto.setColumnName(rs.getString("FKCOLUMN_NAME"));

            dto.setReferenceDatabaseName(rs.getString("PKTABLE_CAT"));
            dto.setReferenceTableName(rs.getString("PKTABLE_NAME"));
            dto.setReferenceColumnName(rs.getString("PKCOLUMN_NAME"));

            dto.setSource(ReferenceDTO.Source.DB);

            list.add(dto);
        }
        return list;
    }

    public static boolean isReferToConditionMatch(JSONObject conditions, JSONObject data) {
        //for each keys in conditions
        for (String key : conditions.keySet()) {

            Object conditionValue = conditions.get(key);
            String dataValue = data.getString(key);

            if (conditionValue instanceof JSONArray) {
                //if array then any of the element in the condition array should match with current value
                if (!contains((JSONArray) conditionValue, dataValue)) {
                    return false;
                }
            } else if (!conditions.getString(key).equals(dataValue)) {
                return false;
            }
        }
        return true;
    }

    public static boolean contains(JSONArray array, String data) {
        for (Iterator<Object> it = array.iterator(); it.hasNext();) {
            if (String.valueOf(it.next()).equals(data)) {
                return true;
            }
        }
        return false;
    }

    public static String getWhereQueryFromConditions(JSONObject conditions) {
        StringBuilder builder = new StringBuilder();
        for (String key : conditions.keySet()) {
            Object v = conditions.get(key);
            if (v instanceof JSONArray) {
                String serialized = ((JSONArray) v).toString();
                builder.append(" and ").append(key).append(" in (").append(serialized.substring(1, serialized.length() - 1)).append(")");
            } else {
                builder.append(" and ").append(key).append("='").append(v).append("'");
            }
        }
        return builder.toString();
    }

    public static List<ExecuteRequest> getExecuteRequestsForReferedByReq(String group, ColumnPath self, ColumnPath referencedBy, String value, boolean isAppend, int rowLimit)
            throws ConnectionDetailNotFound, SQLException, ClassNotFoundException,
            FileNotFoundException, ConfigParsingError, NoParserRegistered {

        String selfPath = self.getPathString();
        String referencedByPath = referencedBy.getPathString();

        String currentInfo = null;
        boolean isSelf = false;
        if (selfPath.equals(referencedByPath)) {
            currentInfo = "SELF";
            isSelf = true;
        } else {
            currentInfo = selfPath + " -> " + referencedByPath;
        }

        String whereClause = "";

        //generation of extra where queries if any
        if (referencedBy.getConditions() != null) {
            whereClause = DBHelper.getWhereQueryFromConditions(referencedBy.getConditions());
        }
        
        TableDTO tableMetaData = DatabaseManager.getInstance().getMetaData(group, referencedBy.getDatabase()).getTableMetaData(referencedBy.getTable());

        String orderByStr = "";
        if(tableMetaData.getPrimaryKey() != null){
            orderByStr = " order by "+tableMetaData.getPrimaryKey()+" DESC ";
        }
        
        boolean isJointTable = false;
        String nextColumnName = null;

        if (tableMetaData.getJointTableMapping() != null) {

            MappingTableDto jointDetail = tableMetaData.getJointTableMapping();
            if (referencedBy.getColumn().equals(jointDetail.getFrom())) {
                nextColumnName = jointDetail.getTo();
                isJointTable = true;
            } else if (referencedBy.getColumn().equals(jointDetail.getTo())) {
                nextColumnName = jointDetail.getFrom();
                isJointTable = true;
            } else {
                isJointTable = false;
            }
        }

        List<ExecuteRequest> requests = new ArrayList<ExecuteRequest>();
        if ((isJointTable && tableMetaData.getJointTableMapping().isIncludeSelf()) || !isJointTable) {
            
            String finalQuery = String.format("select * from %s where %s='%s' %s %s limit %d", referencedBy.getTable(), referencedBy.getColumn(),
                    value, whereClause, orderByStr, rowLimit);
            
            ExecuteRequest request = new ExecuteRequest(finalQuery, referencedBy.getDatabase(), currentInfo, isAppend);
            if (isSelf) {
                request.setRelation(ExecuteRequest.SELF);
            } else {
                request.setRelation(ExecuteRequest.REFERENCED_BY);
            }
            requests.add(request);
        }
        if (isJointTable) {
            List<ColumnPath> refToList = tableMetaData.getColumnMetaData(nextColumnName).getReferTo();
            for (ColumnPath referTo : refToList) {
                TableDTO referToTableMetaData = DatabaseManager.getInstance().getMetaData(group, referTo.getDatabase())
                        .getTableMetaData(referTo.getTable());
                if(referToTableMetaData.getPrimaryKey() != null){
                    orderByStr = " order by "+referToTableMetaData.getPrimaryKey()+" DESC ";
                }else{
                    orderByStr = "";
                }
                String finalQuery = String.format("select * from %s where %s in (select %s from %s where %s='%s' %s) %s limit %d",
                        referTo.getTable(), referTo.getColumn(), nextColumnName,
                        referencedBy.getTable(), referencedBy.getColumn(), value, whereClause, orderByStr, rowLimit);
                String info = "AUTO-RESOLVE : " + currentInfo + " -> " + nextColumnName + " -> " + referTo.getPathString();
                ExecuteRequest request = new ExecuteRequest(finalQuery, referTo.getDatabase(), info, isAppend);
                request.setRelation(ExecuteRequest.REFERENCED_BY);
                requests.add(request);
                isAppend = true;
            }
        }
        return requests;
    }
}
