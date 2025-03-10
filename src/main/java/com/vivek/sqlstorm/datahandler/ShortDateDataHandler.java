/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.datahandler;

import lombok.extern.log4j.Log4j2;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 * @author Vivek
 */
@Log4j2
public class ShortDateDataHandler implements DataHandler{

    private static final SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy");

    public ShortDateDataHandler()
    {
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    @Override
    public String get(String value)
    {
        return df.format(new Date(Long.parseLong(value)));
    }

    @Override
    public String set(String value)
    {
        try
        {
            return ""+df.parse(value).getTime();
        }
        catch (ParseException ex)
        {
            log.error(ex, ex);
            return null;
        }
    }
}
