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
package com.vivek.sqlstorm.connection;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.sqlstorm.config.connection.parsers.DatabaseConfigJsonParser;
import com.vivek.sqlstorm.config.connection.parsers.DatabaseConfigXmlParser;
import com.vivek.sqlstorm.constants.Constants;
import com.vivek.sqlstorm.exceptions.ConnectionDetailNotFound;
import com.vivek.utils.parser.ConfigParserFactory;
import com.vivek.utils.parser.ConfigParsingError;
import com.vivek.utils.parser.NoParserRegistered;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class DatabaseConnectionManager {
    private static final Logger logger = Logger.getLogger(DatabaseConnectionManager.class);
        
    private static DatabaseConnectionManager self = null;
    public static synchronized DatabaseConnectionManager getInstance() throws FileNotFoundException, ConfigParsingError, NoParserRegistered{
        if(self == null){
            self = new DatabaseConnectionManager();
        }
        return self;
    }
     
    
    private ConnectionConfig configs;
    
    private DatabaseConnectionManager() throws FileNotFoundException, ConfigParsingError, NoParserRegistered {
        ConfigParserFactory.registerParser(ConnectionConfig.class, new DatabaseConfigXmlParser());
        ConfigParserFactory.registerParser(ConnectionConfig.class, new DatabaseConfigJsonParser());
        
        connectionMap = new HashMap<String, Map<String, ConnectionInfo>>();
        configs = ConfigParserFactory.getParser(ConnectionConfig.class).parse(Constants.CONNECTION_CONFIGURATION_FILE_NAME);
        for(ConnectionDTO config : configs.getConnections()){
            logger.debug("Connection : "+config);
            addConnectionInfo(new ConnectionInfo(config));
        }
    }
    
    public Set<String> getGroupNames() {
        return connectionMap.keySet();
    }
    
    public Set<String> getDbNames(String group) throws ConnectionDetailNotFound{
        if(!connectionMap.containsKey(group)){
            throw new ConnectionDetailNotFound("Invalid group name :"+group);
        }
        return connectionMap.get(group).keySet();
    }
    
    public boolean isUpdatableConnection(String groupName, String dbName) throws ConnectionDetailNotFound{
        return getConnectionInfo(groupName, dbName).getConfig().isUpdatable();
    }
    
    public boolean isDeletableConnection(String groupName, String dbName) throws ConnectionDetailNotFound{
        return getConnectionInfo(groupName, dbName).getConfig().isDeletable();
    }
    
    public synchronized Connection getConnection(String groupName, String dbName) throws SQLException, ConnectionDetailNotFound, ClassNotFoundException
    {
        ConnectionInfo connInfo = getConnectionInfo(groupName, dbName);
        if(!isValidConnection(connInfo)){
            connInfo.setConnection(createConnection(connInfo.getConfig()));
        }
        return connInfo.getConnection();
    }
    
    public void closeAllConnections()
    {
        for(Map<String, ConnectionInfo> databases : connectionMap.values()){
            for(ConnectionInfo conn : databases.values()){
                conn.closeConnection();
            }
        }
    }
    
    public ConnectionDTO getConnectionConfig(String group, String database) throws ConnectionDetailNotFound{
        return getConnectionInfo(group, database).getConfig();
    }
    
    /////////
    private final Map<String, Map<String, ConnectionInfo>> connectionMap;
    private ConnectionInfo getConnectionInfo(String group, String database) throws ConnectionDetailNotFound{
        Map<String, ConnectionInfo> i = connectionMap.get(group);
        if(i == null){
            throw new ConnectionDetailNotFound("Invalid group name :"+group);
        }
        ConnectionInfo info = i.get(database);
        if(info == null){
            throw new ConnectionDetailNotFound("No database "+database+" in the group :"+group);
        }
        return info;
    }  
    
    private void addConnectionInfo(ConnectionInfo info){
        Map<String, ConnectionInfo> i = connectionMap.get(info.getConfig().getGroup());
        if(i == null){
            i = new ConcurrentHashMap<String, ConnectionInfo>();
            connectionMap.put(info.getConfig().getGroup(), i);
        }
        i.put(info.getConfig().getDbName(), info);
    }
    //////////
    
    
    private boolean isValidConnection(ConnectionInfo connection) throws SQLException{
        return (connection.getConnection() != null 
                && System.currentTimeMillis()-connection.getConnectTime()<configs.getConnectionExpiryTime() 
                && !connection.getConnection().isClosed());
    }
    private Connection createConnection(ConnectionDTO config) throws ClassNotFoundException, SQLException{
        Class.forName(config.getDriverClassName());
        
        //only wait for 10 seconds to connect to the db
        DriverManager.setLoginTimeout(10);
        
        SQLException exception = null;
        for(int i=0; i < configs.getMaxRetryCount(); i++){
            try {
                return DriverManager.getConnection(config.getDatabaseURL(), config.getUser(), config.getPassword());
            } catch (SQLException ex) {
                exception = ex;
            }
        }
        throw exception;
    }
    
    protected void finalized()
    {
        closeAllConnections();
    }
    
    private String getKey(String group, String database){
        return group+"::"+database;
    }
    
    private class ConnectionInfo{
        private long connectTime;
        private ConnectionDTO config;
        private Connection connection;

        public ConnectionInfo(ConnectionDTO config) {
            this.config = config;
        }
        
        public long getConnectTime() {
            return connectTime;
        }

        public ConnectionDTO getConfig() {
            return config;
        }

        public void setConfig(ConnectionDTO config) {
            this.config = config;
        }

        public Connection getConnection() {
            return connection;
        }

        public void setConnection(Connection connection) {
            closeConnection();
            this.connection = connection;
            this.connectTime = System.currentTimeMillis();
        }
        
        public void closeConnection(){
            if(this.connection != null){
                try {
                    this.connection.close();
                } catch (SQLException ex) {
                    logger.error("Error in closing the connection"+ex.getMessage());
                }
            }
        }
        
    }
}
