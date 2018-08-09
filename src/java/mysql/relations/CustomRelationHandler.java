/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mysql.relations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import mysql.MultiMap;
import mysql.ReferenceDTO;
import mysql.resource.ResorceFinder;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author root
 */
public class CustomRelationHandler {
    private static final Logger logger = Logger.getLogger(CustomRelationHandler.class);
    
    private static String fileName = "custom_mapping.json";
    private static JSONObject mapping;

    private static File getFile() throws IOException {
        return ResorceFinder.getFile(fileName);
    }

    private static boolean loadMappingFromFile() throws IOException {
        File file = getFile();
        if (file == null) {
            return false;
        }
        String config = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        mapping = new JSONObject(config);
        return true;
    }
    
    private static boolean init(){
        try {
            return mapping != null || loadMappingFromFile();
        } catch (IOException ex) {
            logger.error(ex);
            return false;
        }
    }
    
    public static MappingTableDto getMappingTableInfo(String database, String table){
        if(!init()){
            return null;
        }
        try {
            JSONObject info = mapping.getJSONObject("databases").getJSONObject(database).getJSONObject("mapping_tables").getJSONObject(table);
            MappingTableDto infoDto = new MappingTableDto();
            infoDto.setType(MappingTableDto.MappingType.valueOf(info.getString("type")));
            infoDto.setFrom(info.getString("from"));
            infoDto.setTo(info.getString("to"));
            return infoDto;
        } catch (NullPointerException e) {
            logger.debug("No mapping found");
        } catch (JSONException ex){
            logger.error("Invalid Config : "+ex.getMessage());
        }
        return null;
    }

    public static void getReferences(String currentDatabase, String currentTableName, MultiMap<String, ReferenceDTO> referedBy, MultiMap<String, ReferenceDTO> referTo) {
        logger.info("Current Database : "+currentDatabase+"; Current Table Name :"+currentTableName);
        try {
            if (mapping != null || loadMappingFromFile()) {
                for (String fromDatabase : mapping.getJSONObject("databases").keySet()) {
                    
                    JSONObject databaseMap = mapping.optJSONObject("databases").optJSONObject(fromDatabase);
                    if (databaseMap == null) {
                        return;
                    }
                    JSONArray relations = databaseMap.getJSONArray("relations");
                    if (relations == null) {
                        return;
                    }
//                    boolean isSameDB = fromDatabase.equals(currentDatabase);
                    
                    ReferenceDTO tmp;
                    for (Object row : relations) {
                        JSONObject rel = (JSONObject) row;
                        
                        String toDatabase = rel.optString("referenced_database_name");
                        //if ref db is not mentioned then consider as current selected db
                        if(toDatabase == null || toDatabase.isEmpty()){
                            toDatabase = fromDatabase;
                        }
                        
                        String fromTable = rel.getString("table_name");
                        String toTable = rel.getString("referenced_table_name");
                        
//                        logger.info(fromDatabase+toDatabase+fromTable+toTable+currentDatabase+currentTableName);
                        
                        if ( (fromDatabase.equals(currentDatabase) && fromTable.equals(currentTableName)) ||
                             (toDatabase.equals(currentDatabase) && toTable.equals(currentTableName)) ) {
                            tmp = new ReferenceDTO();
                            
                            tmp.setDatabaseName(fromDatabase);
                            tmp.setTableName(fromTable);
                            tmp.setColumnName(rel.getString("table_column"));
                            
                            tmp.setReferenceDatabaseName(toDatabase);
                            tmp.setReferenceTableName(toTable);
                            tmp.setReferenceColumnName(rel.getString("referenced_column_name"));
                            tmp.setConditions(rel.optJSONObject("conditions"));
                            tmp.setSource(ReferenceDTO.Source.CUSTOM);
                            
                            if (fromDatabase.equals(currentDatabase) && fromTable.equals(currentTableName)) {
                                referTo.put(tmp.getColumnName(), tmp);
                            }
                            if (toDatabase.equals(currentDatabase) && toTable.equals(currentTableName)) {
                                referedBy.put(tmp.getReferenceColumnName(), tmp);
                            }
                        }
                    }
                }
            }
        } catch (NullPointerException e) {
            logger.error(e);
        } catch (IOException ex) {
            logger.error(ex);
        }
    }
}
