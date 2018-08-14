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
package com.vivek.sqlstorm;

import com.vivek.sqlstorm.connection.DatabaseConnectionManager;
import com.vivek.sqlstorm.dto.DatabaseDTO;
import com.vivek.sqlstorm.dto.TableDTO;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.sqlstorm.metadata.DatabaseMetaDataManager;
import com.vivek.utils.parser.ConfigParsingError;
import com.vivek.utils.parser.NoParserRegistered;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class DatabaseManager {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class);
    
    private static DatabaseManager self = null;
    public static synchronized DatabaseManager getInstance() throws FileNotFoundException, ConfigParsingError, NoParserRegistered
    {
        if(self == null){
            self = new DatabaseManager();
        }
        return self;
    }
    
    private final DatabaseConnectionManager connectionManager;
    private final DatabaseMetaDataManager metaDataManager;
    
    private DatabaseManager() throws FileNotFoundException, ConfigParsingError, NoParserRegistered
    {
        connectionManager = DatabaseConnectionManager.getInstance();
        metaDataManager = DatabaseMetaDataManager.getInstance();
    }
    
    //wrapping database connection manager 
    public Connection getConnection(String groupName, String dbName) throws SQLException, ConnectionDetailNotFound, ClassNotFoundException 
    {
        return connectionManager.getConnection(groupName, dbName);
    }

    public boolean isUpdatableConnection(String groupName, String dbName) throws ConnectionDetailNotFound{
        return connectionManager.isUpdatableConnection(groupName, dbName);
    }
    
    public boolean isDeletableConnection(String groupName, String dbName) throws ConnectionDetailNotFound{
        return connectionManager.isDeletableConnection(groupName, dbName);
    }
    // end
    
    
    //warapping meta-data related functions
    public Set<String> getGroupNames() {
        return metaDataManager.getGroupNames();
    }
    
    public Set<String> getDbNames(String groupName) throws ConnectionDetailNotFound{
        return metaDataManager.getDbNames(groupName);
    }
    
    public Collection<TableDTO> getTables(String group, String database) throws ConnectionDetailNotFound, SQLException, ClassNotFoundException{
        return metaDataManager.getTables(group, database);
    }
    
    public DatabaseDTO getMetaData(String group, String database) throws ConnectionDetailNotFound, SQLException, ClassNotFoundException{
        return metaDataManager.getMetaData(group, database);
    }
}
