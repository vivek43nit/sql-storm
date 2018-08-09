/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package databasemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mysql.resource.ResorceFinder;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 *
 * @author root
 */
public class DBXmlParser {
    
    private String m_xmlFileName;

    public DBXmlParser(String xmlFileName)
    {
        m_xmlFileName = xmlFileName;
    }
    
    public InputStream getXMLFileInputStream() throws IOException
    {
        File file = ResorceFinder.getFile(m_xmlFileName);
        if(file.exists())
        {
            FileInputStream is = new FileInputStream(file);
            return is;
        }else{
            return null;
        }
    }
    
    public List<DBConnectionDTO> getAllDatabaseConnection() throws JDOMException, IOException
    {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(getXMLFileInputStream());
        Element root = doc.getRootElement();
        List connectionList = root.getChildren("CONNECTION");
        
        List<DBConnectionDTO> list = new ArrayList<DBConnectionDTO>();
        
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
            
            DBConnectionDTO connConfig = new DBConnectionDTO(DRIVER_CLASS_NAME, DATABASE_URL, USER_NAME, PASSWORD, Long.parseLong(ID), group, DB_NAME);
            if(UPDATABLE != null && !UPDATABLE.isEmpty() && !UPDATABLE.equals("null")){
                connConfig.setUpdatable(true);
            }
            if(DELETABLE != null && !DELETABLE.isEmpty() && !DELETABLE.equals("null")){
                connConfig.setDeletable(true);
            }
            list.add(connConfig);
        }
        return list;
    }
}
