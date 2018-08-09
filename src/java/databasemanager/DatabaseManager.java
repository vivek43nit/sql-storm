/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package databasemanager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.jdom.JDOMException;

/**
 *
 * @author root
 */
public class DatabaseManager {
   
    private static final Logger logger = Logger.getLogger(DatabaseManager.class);
    
    private static DatabaseManager self = null;

    private final long CONNECTION_HOLD_TIME;
    
    private final Map<String, Connection> dbNameToConnectionMap;
    private final Map<String, Map<String, DBConnectionDTO>> dbNameToConnectionConfigMap;
    
    private Map<Connection, Long> connectionTimeMap;
   
    public static String XML_FILE_NAME = "DatabaseConnection.xml";
    
    private DatabaseManager()
    {
        CONNECTION_HOLD_TIME = 0x36ee80L;
        dbNameToConnectionConfigMap = new HashMap<String, Map<String, DBConnectionDTO>>();
        dbNameToConnectionMap = new HashMap<String, Connection>();
        connectionTimeMap = new HashMap<Connection, Long>();
    }
    
    public Set<String> getGroupNames() {
        return dbNameToConnectionConfigMap.keySet();
    }
    
    public Set<String> getDbNames(String groupName){
        return dbNameToConnectionConfigMap.get(groupName).keySet();
    }
    
    public boolean isUpdatableConnection(String groupName, String dbName){
        return dbNameToConnectionConfigMap.get(groupName).get(dbName).isUpdatable();
    }
    
    public boolean isDeletableConnection(String groupName, String dbName){
        return dbNameToConnectionConfigMap.get(groupName).get(dbName).isDeletable();
    }
    
    public static DatabaseManager getInstance() throws JDOMException, SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException
    {
        if(self == null)
            createDBManager();
        return self;
    }
    
    private static synchronized void createDBManager() throws JDOMException, SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException
    {
        if(self == null)
        {
            DatabaseManager temp_databaseManager = new DatabaseManager();
            for(int i = 0; i < 20;)
                try
                {
                    temp_databaseManager.reLoad();
                    break;
                }
                catch(Throwable t)
                {
                    logger.fatal(t.getMessage(), t);
                    try
                    {
                        Thread.sleep(i * 1000);
                    }
                    catch(InterruptedException ex)
                    {
                        logger.fatal("Got Exception :", ex);
                    }
                    i++;
                }
            self = temp_databaseManager;
        }
    }

    public void reLoad() throws JDOMException, SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException, IOException
    {
        try
        {
            closeAllConnections();
            DBXmlParser xml = new DBXmlParser(XML_FILE_NAME);
            List<DBConnectionDTO> dbConfigs = xml.getAllDatabaseConnection();
            
            //regisering driver
            DriverManager.registerDriver((Driver)Class.forName(dbConfigs.get(0).getDriverClassName()).newInstance());
            
            for(DBConnectionDTO config : dbConfigs){
                Map<String, DBConnectionDTO> groupConfigs = dbNameToConnectionConfigMap.get(config.getGroup());
                if(groupConfigs == null){
                    groupConfigs = new HashMap<String, DBConnectionDTO>();
                    dbNameToConnectionConfigMap.put(config.getGroup(), groupConfigs);
                }
                groupConfigs.put(config.getDbName(), config);
            }
        }
        catch(JDOMException e1)
        {
            logger.fatal(e1.getMessage(), e1);
            throw e1;
        }
        catch(SQLException e2)
        {
            logger.fatal(e2.getMessage(), e2);
            throw e2;
        }
        catch(ClassNotFoundException e3)
        {
            logger.fatal(e3.getMessage(), e3);
            throw e3;
        }
        catch(IllegalAccessException e4)
        {
            logger.fatal(e4.getMessage(), e4);
            throw e4;
        }
        catch(InstantiationException e5)
        {
            logger.fatal(e5.getMessage(), e5);
            throw e5;
        }
    }

    public synchronized Connection getConnection(String groupName, String dbName) throws SQLException
    {
        String key = groupName+"-"+dbName;
        
        Connection connection = null;
        try
        {
            do
            {
                do
                {
                    if(!dbNameToConnectionMap.containsKey(key)){
                        DBConnectionDTO config = dbNameToConnectionConfigMap.get(groupName).get(dbName);
                        Connection conn = DriverManager.getConnection(config.getDatabaseURL(), config.getUser(), config.getPassword());
                        dbNameToConnectionMap.put(key, conn);
                        connectionTimeMap.put(conn, new Long(System.currentTimeMillis()));
                    }
                    connection = dbNameToConnectionMap.get(key);
                }while(connection == null || connection.isClosed());
                
                Long creationTime = (Long)connectionTimeMap.get(connection);
                //if connection is good to have then return this connection
                if(creationTime.longValue() + CONNECTION_HOLD_TIME > System.currentTimeMillis())
                    break;
                
                //if connection is expired
                connectionTimeMap.remove(connection);
                dbNameToConnectionMap.remove(key);
                
                //safely try to close the connection
                try
                {
                    connection.close();
                }
                catch(Throwable th)
                {
                    logger.fatal("Failed to Close Connection", th);
                }
            } while(true);
        }
        catch(SQLException e2)
        {
            logger.fatal(e2.getMessage(), e2);
            throw e2;
        }
        return connection;
    }

    public void closeAllConnections()
    {
        for(Connection con : connectionTimeMap.keySet()){
            try
            {
                if(!con.isClosed())
                    con.close();
            }
            catch(Exception ex)
            {
                logger.fatal(ex.getMessage(), ex);
            }
        }
        dbNameToConnectionMap.clear();
        connectionTimeMap.clear();
    }

    protected void finalized()
    {
        closeAllConnections();
    }
}
