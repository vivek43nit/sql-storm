/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mysql.util;

/**
 *
 * @author root
 */
public class CommonFunctions {
    
    public static String getName(String database, String table, String column){
        return database+"."+table+"."+column;
    }
}
