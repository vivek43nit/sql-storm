/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.metadata;

import lombok.extern.log4j.Log4j2;


/**
 *
 * @author Vivek
 */
@Log4j2
public class GetReferenceDAO {

//
//    public static void get(String groupName, String dbName, String tableName, MultiMap<String, ReferenceDTO> referedBy, MultiMap<String, ReferenceDTO> referTo) throws Exception {
//        ReferenceDTO tmp;
//        Connection con = null;
//        con = DatabaseManager.getInstance().getConnection(groupName, dbName);
//
//        String sql = "select TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME"
//                + " from INFORMATION_SCHEMA.KEY_COLUMN_USAGE"
//                + " where CONSTRAINT_SCHEMA=? and (REFERENCED_TABLE_NAME = ? or (TABLE_NAME=? and REFERENCED_TABLE_NAME <> ''))";
//
//        log.info(sql + ";" + groupName + "," + dbName + "," + tableName);
//
//        PreparedStatement ps = con.prepareStatement(sql);
//        ps.setString(1, dbName);
//        ps.setString(2, tableName);
//        ps.setString(3, tableName);
//
//        ResultSet refer = ps.executeQuery();
//        while (refer.next()) {
//            tmp = new ReferenceDTO();
//            
//            tmp.setDatabaseName(dbName);
//            tmp.setTableName(refer.getString(1));
//            tmp.setColumnName(refer.getString(2));
//            
//            tmp.setReferenceDatabaseName(dbName);
//            tmp.setReferenceTableName(refer.getString(3));
//            tmp.setReferenceColumnName(refer.getString(4));
//
//            tmp.setSource(ReferenceDTO.Source.DB);
//            
//            if (tmp.getTableName().equals(tableName)) {
//                referTo.put(tmp.getColumnName(), tmp);
//            }
//            if (tmp.getReferenceTableName().equals(tableName)) {
//                referedBy.put(tmp.getReferenceColumnName(), tmp);
//            }
//        }
//        refer.close();
//        ps.close();
//    }
}
