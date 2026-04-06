package com.vivek.sqlstorm.config.connection.parsers;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.utils.parser.ConfigParserInterface;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DatabaseConfigXmlParser implements ConfigParserInterface<ConnectionConfig> {

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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            Element root = doc.getDocumentElement();

            NodeList connectionList = root.getElementsByTagName("CONNECTION");
            List<ConnectionDTO> list = new ArrayList<>();

            for (int i = 0; i < connectionList.getLength(); i++) {
                Element connection = (Element) connectionList.item(i);

                String ID = connection.getAttribute("ID");
                String DRIVER_CLASS_NAME = connection.getAttribute("DRIVER_CLASS_NAME");
                String DATABASE_URL = connection.getAttribute("DATABASE_URL");
                String USER_NAME = connection.getAttribute("USER_NAME");
                String PASSWORD = connection.getAttribute("PASSWORD");
                String group = connection.getAttribute("GROUP");
                String DB_NAME = connection.getAttribute("DB_NAME");
                String UPDATABLE = connection.getAttribute("UPDATABLE");
                String DELETABLE = connection.getAttribute("DELETABLE");
                String searchableRowLimit = connection.getAttribute("NON_INDEXED_SEARCHABLE_ROW_LIMIT");

                ConnectionDTO connConfig = new ConnectionDTO(DRIVER_CLASS_NAME, DATABASE_URL, USER_NAME, PASSWORD, Long.parseLong(ID), group, DB_NAME);

                if (UPDATABLE != null && !UPDATABLE.isEmpty() && !UPDATABLE.equals("null")) {
                    connConfig.setUpdatable(true);
                }
                if (DELETABLE != null && !DELETABLE.isEmpty() && !DELETABLE.equals("null")) {
                    connConfig.setDeletable(true);
                }
                if (searchableRowLimit != null && !searchableRowLimit.isEmpty()) {
                    connConfig.setSearchableRowLimit(Integer.parseInt(searchableRowLimit));
                }

                list.add(connConfig);
            }

            ConnectionConfig config = new ConnectionConfig(list);
            String connectionExpiryTime = root.getAttribute("CONNECTION_EXPIRY_TIME");
            String maxRetryCount = root.getAttribute("MAX_RETRY_COUNT");

            if (connectionExpiryTime != null && !connectionExpiryTime.isEmpty()) {
                config.setConnectionExpiryTime(Long.parseLong(connectionExpiryTime));
            }
            if (maxRetryCount != null && !maxRetryCount.isEmpty()) {
                config.setMaxRetryCount(Integer.parseInt(maxRetryCount));
            }

            return config;
        } catch (Exception ex) {
            logger.error("Error in Parsing XML file", ex);
            return null;
        }
    }
}
