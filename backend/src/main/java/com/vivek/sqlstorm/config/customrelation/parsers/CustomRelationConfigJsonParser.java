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
package com.vivek.sqlstorm.config.customrelation.parsers;

import com.vivek.sqlstorm.config.customrelation.DatabaseConfig;
import com.vivek.utils.parser.ConfigParserInterface;
import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.MappingTableDto;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class CustomRelationConfigJsonParser implements ConfigParserInterface<CustomRelationConfig> {

    private static final Logger logger = Logger.getLogger(CustomRelationConfigJsonParser.class);

    @Override
    public String getApplicationName() {
        return "sql-storm";
    }

    @Override
    public String getSupportedExtension() {
        return "json";
    }

    @Override
    public CustomRelationConfig parse(File f) {
        try {
            String str = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())));
            JSONObject config = new JSONObject(str);

            Map<String, DatabaseConfig> databaseMap = new HashMap<String, DatabaseConfig>();
            JSONObject databases = config.optJSONObject("databases");
            if (databases == null) {
                return new CustomRelationConfig(databaseMap);
            }
            for (String database : databases.keySet()) {
                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setRelations(getRelations(database, databases.getJSONObject(database).optJSONArray("relations")));
                dbConfig.setJointTables(getJointTables(databases.getJSONObject(database).optJSONObject("mapping_tables")));
                dbConfig.setAutoResolve(getAutoResolveTables(databases.getJSONObject(database).optJSONObject("auto_resolve")));
                databaseMap.put(database, dbConfig);
            }
            return new CustomRelationConfig(databaseMap);
        } catch (IOException ex) {
            logger.error("Error in parsing the json file", ex);
            return null;
        }
    }

    private List<ReferenceDTO> getRelations(String database, JSONArray relations) {
        if (relations == null || relations.length() == 0) {
            return Collections.emptyList();
        }
        List<ReferenceDTO> list = new ArrayList<ReferenceDTO>();
        ReferenceDTO tmp;
        for (Object row : relations) {
            JSONObject rel = (JSONObject) row;
            tmp = new ReferenceDTO();
            tmp.setDatabaseName(database);
            tmp.setTableName(rel.getString("table_name"));
            tmp.setColumnName(rel.getString("table_column"));
            
            //optional referenced_database_name
            tmp.setReferenceDatabaseName(rel.optString("referenced_database_name", database));
            
            tmp.setReferenceTableName(rel.getString("referenced_table_name"));
            tmp.setReferenceColumnName(rel.getString("referenced_column_name"));
            
            //optional conditions
            tmp.setConditions(rel.optJSONObject("conditions"));
            
            tmp.setSource(ReferenceDTO.Source.CUSTOM);
            list.add(tmp);
        }
        return list;
    }
    
    private Map<String, MappingTableDto> getJointTables(JSONObject jointTables){
        Map<String, MappingTableDto> map = new HashMap<String, MappingTableDto>();
        if(jointTables == null || jointTables.length() == 0){
            return map;
        }
        
        for(String table : jointTables.keySet()){
            JSONObject info = jointTables.getJSONObject(table);
            MappingTableDto infoDto = new MappingTableDto();
            infoDto.setType(MappingTableDto.MappingType.valueOf(info.getString("type")));
            infoDto.setFrom(info.getString("from"));
            infoDto.setTo(info.getString("to"));
            infoDto.setIncludeSelf(info.optBoolean("include-self", false));
            map.put(table, infoDto);
        }
        return map;
    }
    
    private Map<String, List<String>> getAutoResolveTables(JSONObject autoResolves){
        Map<String, List<String>> autoResolveMap = new HashMap<String, List<String>>();
        if(autoResolves == null){
            return autoResolveMap;
        }
        for(String table : autoResolves.keySet()){
            JSONArray columns = autoResolves.getJSONArray(table);
            List<String> list = new ArrayList<String>();
            for(int i=0; i < columns.length(); i++){
                list.add(columns.getString(i));
            }
            autoResolveMap.put(table, list);
        }
        return autoResolveMap;
    }
    
}
