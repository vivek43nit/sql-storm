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
package com.vivek.sqlstorm.config.connection.parsers;

import com.vivek.sqlstorm.config.connection.ConnectionConfig;
import com.vivek.sqlstorm.config.connection.ConnectionDTO;
import com.vivek.utils.parser.ConfigParserInterface;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Vivek Kumar <vivek43nit@gmail.com>
 */
public class DatabaseConfigJsonParser implements ConfigParserInterface<ConnectionConfig>{
    
    private static final Logger logger = Logger.getLogger(DatabaseConfigJsonParser.class);
    
    @Override
    public String getApplicationName() {
        return "sql-storm";
    }

    @Override
    public String getSupportedExtension() {
        return "json";
    }

    @Override
    public ConnectionConfig parse(File f){
        try {
            String config = new String(Files.readAllBytes(Paths.get(f.getAbsolutePath())));
            JSONObject root  = new JSONObject(config);
            JSONArray connections = root.getJSONArray("connections");
            List<ConnectionDTO> list = new ArrayList<ConnectionDTO>();
            
            for(Object conn : connections){
                JSONObject connection = (JSONObject)conn;
                
                ConnectionDTO dto = new ConnectionDTO();
                dto.setId(connection.getInt("ID"));
                dto.setDriverClassName(connection.getString("DRIVER_CLASS_NAME"));
                dto.setDatabaseURL(connection.getString("DATABASE_URL"));
                dto.setUser(connection.getString("USER_NAME"));
                dto.setPassword(connection.getString("PASSWORD"));
                dto.setGroup(connection.getString("GROUP"));
                dto.setDbName(connection.getString("DB_NAME"));
                dto.setUpdatable(connection.optBoolean("UPDATABLE", false));
                dto.setDeletable(connection.optBoolean("DELETABLE", false));
                dto.setSearchableRowLimit(connection.optInt("NON_INDEXED_SEARCHABLE_ROW_LIMIT", dto.getSearchableRowLimit()));
                list.add(dto);
            }
            
            ConnectionConfig conf = new ConnectionConfig(list);
            conf.setConnectionExpiryTime(root.optLong("connection_expiry_time", conf.getConnectionExpiryTime()));
            conf.setMaxRetryCount(root.optInt("max_retry_count", conf.getMaxRetryCount()));
            return conf;
        } catch (IOException ex) {
            logger.error("Unable to parse json file",ex);
            return null;
        }
    }
    
}
