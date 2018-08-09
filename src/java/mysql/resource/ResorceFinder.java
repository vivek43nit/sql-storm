/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mysql.resource;

import databasemanager.DBXmlParser;
import java.io.File;

/**
 *
 * @author root
 */
public class ResorceFinder {
    
    public static File getFile(String fileName){
        File file = new File("/etc/sql-storm/"+fileName);
        if (file.exists()) {
            return file;
        }
        file = new File(fileName);
        if (file.exists()) {
            return file;
        }
        file = new File(DBXmlParser.class.getClassLoader().getResource(fileName).getFile());
        if (file != null) {
            return file;
        } else {
            return null;
        }
    }
}
