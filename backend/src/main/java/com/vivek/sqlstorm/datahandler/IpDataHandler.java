/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.datahandler;

/**
 *
 * @author Vivek
 */
public class IpDataHandler implements DataHandler{

    @Override
    public String get(String value)
    {
        long ip = Long.parseLong(value);
        return ((ip >>> 24) & 0xFF) + "."
                + ((ip >>> 16) & 0xFF) + "."
                + ((ip >>> 8) & 0xFF) + "."
                + (ip & 0xFF);
    }

    @Override
    public String set(String value)
    {
        long result = 0;
        if (value.length() == 0)
        {
            return "0";
        }
        String[] ipAddressInArray = value.split("\\.");
        for (int i = 3; i >= 0; i--)
        {
            long ip = Long.parseLong(ipAddressInArray[3 - i]);
            result |= ip << (i * 8);
        }
        return ""+result;
    }
    
}
