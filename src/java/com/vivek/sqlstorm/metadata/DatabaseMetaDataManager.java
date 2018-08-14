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
package com.vivek.sqlstorm.metadata;

import com.vivek.sqlstorm.config.customrelation.CustomRelationConfig;
import com.vivek.sqlstorm.config.customrelation.DatabaseConfig;
import com.vivek.sqlstorm.config.customrelation.parsers.CustomRelationConfigJsonParser;
import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.constants.Constants;
import com.vivek.sqlstorm.dto.ColumnDTO;
import com.vivek.sqlstorm.dto.ColumnPath;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.IndexInfo;
import com.vivek.sqlstorm.dto.MappingTableDto;
import com.vivek.sqlstorm.dto.ReferenceDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.sqlstorm.utils.DBHelper;
import com.vivek.utils.MultiMap;
import com.vivek.utils.parser.ConfigParserFactory;
import com.vivek.utils.parser.ConfigParsingError;
import com.vivek.utils.parser.NoParserRegistered;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class DatabaseMetaDataManager {
    private static final Logger logger = Logger.getLogger(DatabaseMetaDataManager.class);
        
    private static DatabaseMetaDataManager self = null;
    public static synchronized DatabaseMetaDataManager getInstance() throws FileNotFoundException, ConfigParsingError, NoParserRegistered
    {
        if(self == null){
            self = new DatabaseMetaDataManager();
        }
        return self;
    }

    private CustomRelationConfig config;
    private DatabaseConnectionManager connectionManager;
    public DatabaseMetaDataManager() throws FileNotFoundException, ConfigParsingError, NoParserRegistered {
        //registering parser for CustomRelationConfig
        ConfigParserFactory.registerParser(CustomRelationConfig.class, new CustomRelationConfigJsonParser());
        
        this.config = ConfigParserFactory.getParser(CustomRelationConfig.class).parse(Constants.CUSTOM_RELATION_CONFIG_FILE_NAME);
        logger.debug("Loaded Config : "+config.toString());
        this.connectionManager = DatabaseConnectionManager.getInstance();
        init();
    }
    
    public Set<String> getGroupNames() {
        return connectionManager.getGroupNames();
    }
    
    public Set<String> getDbNames(String groupName) throws ConnectionDetailNotFound{
        return connectionManager.getDbNames(groupName);
    }
    
    public synchronized Collection<TableDTO> getTables(String group, String database) throws ConnectionDetailNotFound, SQLException, ClassNotFoundException{
        DatabaseDTO dbmeta = getMetaData(group, database);
        Collection<TableDTO> tableCollection = dbmeta.getTables();
        
        List tables;
        if (tableCollection instanceof List)
            tables = (List)tableCollection;
        else
            tables = new ArrayList(tableCollection);

        Collections.sort(tables, new  Comparator<TableDTO>() {
            @Override
            public int compare(TableDTO o1, TableDTO o2) {
                return o2.getWeight() - o1.getWeight();
            }
        });
        return tables;
    }
    
    private void lazyLoadFromDb(DatabaseDTO dbmeta) throws SQLException, ConnectionDetailNotFound, ClassNotFoundException{
        Connection con = connectionManager.getConnection(dbmeta.getGroup(), dbmeta.getName());
        List<TableDTO> dbtables = DBHelper.getTables(con);
        
        //updating all the table and columns information from db to meta data cache
        for(TableDTO dbtable : dbtables){
            
            TableDTO t = dbmeta.getTableMetaData(dbtable.getTableName());
            if(t == null){
                dbmeta.addTableMetaData(dbtable);
                t = dbtable;
            }else{
                t.setRemark(dbtable.getRemark());
            }
            
            //updating columns in the table meta data
            List<ColumnDTO> db_columns = DBHelper.getColumns(con, dbtable.getTableName());
            List<String> colNames = new ArrayList<String>();
            
            for(ColumnDTO dbcolumn : db_columns){
                colNames.add(dbcolumn.getName());
                ColumnDTO c = t.getColumnMetaData(dbcolumn.getName());
                if(c == null){
                    t.setColumnMetaData(dbcolumn);
                    c = dbcolumn;
                }else{
                    c.setDataType(dbcolumn.getDataType());
                    c.setDescription(dbcolumn.getDescription());
                    c.setNullable(dbcolumn.getNullable());
                    c.setSize(dbcolumn.getSize());
                }
            }
            //setting col names to maintain the order of columns if required
            t.setColumnNamesInDbOrder(colNames);
            
            //adding indexing information to the columns
            List<IndexInfo> indexList = DBHelper.getAllIndexedColumns(con, dbtable.getTableName());
            logger.debug("Indexes for Table "+dbtable.getTableName()+" : "+indexList);
            t.setIndexingInfo(indexList);
        }
        
        for(TableDTO dbtable : dbtables){
            List<ReferenceDTO> relations = DBHelper.getAllForeignKeys(con, dbtable.getTableName());
            logger.debug("Relations for Table "+dbtable.getTableName()+" : "+relations);
            
            for(ReferenceDTO relation : relations){
                ColumnPath referTo = new ColumnPath(relation.getReferenceDatabaseName(), relation.getReferenceTableName(), relation.getReferenceColumnName());
                ColumnPath referedBy = new ColumnPath(relation.getDatabaseName(), relation.getTableName(), relation.getColumnName());
                referTo.setConditions(relation.getConditions());
                referTo.setSource(ReferenceDTO.Source.DB);
                
                referedBy.setConditions(relation.getConditions());
                referedBy.setSource(ReferenceDTO.Source.DB);
                
                databaseMetaDataMap.get(dbmeta.getGroup()).get(referTo.getDatabase()).getOrAddTableMetaData(referTo.getTable())
                            .getOrAddColumnMetaData(referTo.getColumn()).addReferencedBy(referedBy);
                
                try{
                    getWithoutCheckMetaData(dbmeta.getGroup(), referedBy.getDatabase()).getOrAddTableMetaData(referedBy.getTable())
                        .getOrAddColumnMetaData(referedBy.getColumn()).addReferTo(referTo);
                }catch(ConnectionDetailNotFound ex){
                    logger.warn("DB Config missing for :"+dbmeta);
                }
            }
        }
        
        dbmeta.setLoadedFromDb(true);
    }
    
    private void init(){
        this.databaseMetaDataMap = new HashMap<String, Map<String, DatabaseDTO>>();
        
        //tmp cache
        MultiMap<String, String> databaseToGroup = new MultiMap<String, String>();
        
        //loading all groups and its tables
        Set<String> groups = connectionManager.getGroupNames();
        for(String group : groups){
            try {
                Set<String> databases = connectionManager.getDbNames(group);
                for(String database : databases){
                    addMetaData(new DatabaseDTO(group, database));
                    databaseToGroup.put(database, group);
                }
            } catch (ConnectionDetailNotFound ex) {
                //never possible case
            }
        }
        
        //loading all custom relations
        for(Map.Entry<String,DatabaseConfig> databases : this.config.getDatabases().entrySet()){
            if(!databaseToGroup.containsKey(databases.getKey())){
                logger.warn("database connection definition not found. So dropping this database to load :"+databases.getKey());
                continue;
            }
            String databaseName = databases.getKey();
            DatabaseConfig dbConfig = databases.getValue();
            
            //setting auto resolve table details
            for(Map.Entry<String, List<String>> tables : dbConfig.getAutoResolve().entrySet()){
                String tableName = tables.getKey();
                for(String group : databaseToGroup.get(databaseName)){
                    databaseMetaDataMap.get(group).get(databaseName).getOrAddTableMetaData(tableName).setAutoResolveColumns(tables.getValue());
                }
            }
            
            //setting joint table details
            for(Map.Entry<String, MappingTableDto> tables : dbConfig.getJointTables().entrySet()){
                String tableName = tables.getKey();
                for(String group : databaseToGroup.get(databaseName)){
                    databaseMetaDataMap.get(group).get(databaseName).getOrAddTableMetaData(tableName).setJointTableMapping(tables.getValue());
                }
            }
            
            //setting all the relations
            for(ReferenceDTO relation : dbConfig.getRelations()){
                ColumnPath referTo = new ColumnPath(relation.getReferenceDatabaseName(), relation.getReferenceTableName(), relation.getReferenceColumnName());
                ColumnPath referedBy = new ColumnPath(relation.getDatabaseName(), relation.getTableName(), relation.getColumnName());
                
                referTo.setConditions(relation.getConditions());
                referTo.setSource(ReferenceDTO.Source.CUSTOM);
                
                referedBy.setConditions(relation.getConditions());
                referedBy.setSource(ReferenceDTO.Source.CUSTOM);
                
                for(String group : databaseToGroup.get(databaseName)){
                    databaseMetaDataMap.get(group).get(referTo.getDatabase()).getOrAddTableMetaData(referTo.getTable())
                            .getOrAddColumnMetaData(referTo.getColumn()).addReferencedBy(referedBy);
                    if(databaseToGroup.containsKey(referedBy.getDatabase())){
                        databaseMetaDataMap.get(group).get(referedBy.getDatabase()).getOrAddTableMetaData(referedBy.getTable())
                            .getOrAddColumnMetaData(referedBy.getColumn()).addReferTo(referTo);
                    }
                }
            }
        }
    }
    
    private Map<String, Map<String, DatabaseDTO>> databaseMetaDataMap;
    private void addMetaData(DatabaseDTO metaData){
        Map<String, DatabaseDTO> groups = databaseMetaDataMap.get(metaData.getGroup());
        if(groups == null){
            groups = new HashMap<String, DatabaseDTO>();
            databaseMetaDataMap.put(metaData.getGroup(), groups);
        }
        groups.put(metaData.getName(), metaData);
    }
    
    public DatabaseDTO getWithoutCheckMetaData(String group, String database) throws ConnectionDetailNotFound{
        Map<String, DatabaseDTO> databases = databaseMetaDataMap.get(group);
        if(databases == null){
            throw new ConnectionDetailNotFound("Invalid group name :"+group);
        }
        DatabaseDTO info = databases.get(database);
        if(info == null){
            throw new ConnectionDetailNotFound("No database "+database+" in the group :"+group);
        }
        return info;
    }
    
    public DatabaseDTO getMetaData(String group, String database) throws ConnectionDetailNotFound, SQLException, ClassNotFoundException{
        DatabaseDTO dbmeta = getWithoutCheckMetaData(group, database);
        if(!dbmeta.isLoadedFromDb()){
            lazyLoadFromDb(dbmeta);
        }
        return dbmeta;
    }
    
}
