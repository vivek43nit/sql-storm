/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.datahandler;

import java.util.HashMap;

import lombok.extern.log4j.Log4j2;


/**
 *
 * @author Vivek
 */
@Log4j2
public class DataManager {
    
    private static HashMap<String, DataHandler> converters;
    
    public static String get(String dataType, String data){
        if(converters == null){
            init();
        }
        if(dataType == null || dataType.isEmpty()){
            return data;
        }
        DataHandler handler = converters.get(dataType);
        if(handler == null){
            log.info("Invalid dataType : "+dataType);
            return data;
        }
        return handler.get(data);
    }
    
    public static String set(String dataType, String data){
        if(converters == null){
            init();
        }
        if(dataType == null || dataType.isEmpty()){
            return data;
        }
        dataType = dataType.toLowerCase();
        DataHandler handler = converters.get(dataType);
        if(handler == null){
            log.info("Invalid dataType : "+dataType);
            return data;
        }
        return handler.set(data);
    }
    
    private static void init(){
        converters = new HashMap<String, DataHandler>();
        converters.put("ip", new IpDataHandler());
        converters.put("short-date", new ShortDateDataHandler());
        converters.put("long-date", new LongDateDataHandler());
    }
    
}
