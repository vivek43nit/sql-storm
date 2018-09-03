/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.config.connection.parsers;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.utils.parser.ConfigParserInterface;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 *
 * @author root
 */
public class DatabaseConfigXmlParser implements ConfigParserInterface<ConnectionConfig>{
    
    private static final Logger logger = Logger.getLogger(DatabaseConfigXmlParser.class);
            
    @Override
    public String getApplicationName() {
        return "sql-storm";
    }

    @Override
    public String getSupportedExtension() {
        return "xml";
    }

    @Override
    public ConnectionConfig parse(File file) {
        try {
            SAXBuilder builder = new SAXBuilder();
            Document doc = builder.build(file);
            Element root = doc.getRootElement();
            
            List connectionList = root.getChildren("CONNECTION");
            List<ConnectionDTO> list = new ArrayList<ConnectionDTO>();
            for(Iterator connectionIterator = connectionList.iterator(); connectionIterator.hasNext();)
            {
                Element connection = (Element)connectionIterator.next();
                
                String ID = connection.getAttributeValue("ID");
                String DRIVER_CLASS_NAME = connection.getAttributeValue("DRIVER_CLASS_NAME");
                String DATABASE_URL = connection.getAttributeValue("DATABASE_URL");
                String USER_NAME = connection.getAttributeValue("USER_NAME");
                String PASSWORD = connection.getAttributeValue("PASSWORD");
                String group = connection.getAttributeValue("GROUP");
                String DB_NAME = connection.getAttributeValue("DB_NAME");
                
                String UPDATABLE = connection.getAttributeValue("UPDATABLE");
                String DELETABLE = connection.getAttributeValue("DELETABLE");
                String searchableRowLimit = connection.getAttributeValue("NON_INDEXED_SEARCHABLE_ROW_LIMIT");
                
                ConnectionDTO connConfig = new ConnectionDTO(DRIVER_CLASS_NAME, DATABASE_URL, USER_NAME, PASSWORD, Long.parseLong(ID), group, DB_NAME);
                if(UPDATABLE != null && !UPDATABLE.isEmpty() && !UPDATABLE.equals("null")){
                    connConfig.setUpdatable(true);
                }
                if(DELETABLE != null && !DELETABLE.isEmpty() && !DELETABLE.equals("null")){
                    connConfig.setDeletable(true);
                }
                
                if(searchableRowLimit != null && !searchableRowLimit.isEmpty()){
                    connConfig.setSearchableRowLimit(Integer.parseInt(searchableRowLimit));
                }
                
                list.add(connConfig);
            }
            ConnectionConfig config = new ConnectionConfig(list);
            String connectionExpiryTime = root.getAttributeValue("CONNECTION_EXPIRY_TIME");
            String maxRetryCount = root.getAttributeValue("MAX_RETRY_COUNT");
            
            if(connectionExpiryTime != null && !connectionExpiryTime.isEmpty()){
                config.setConnectionExpiryTime(Long.parseLong(connectionExpiryTime));
            }
            if(maxRetryCount != null && !maxRetryCount.isEmpty()){
                config.setMaxRetryCount(Integer.parseInt(maxRetryCount));
            }
            return config;
        } catch (JDOMException ex) {
            logger.error("Error in Parsing XML file", ex);
            return null;
        }
    }
}
