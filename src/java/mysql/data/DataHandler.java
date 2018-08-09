/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mysql.data;

/**
 *
 * @author Vivek
 */
public interface DataHandler {

    /**
     * This function converts data from db to output to be displayed
     * @param value value from db to be converted
     * @return returns converted value
     */
    public String get(String value);

    /**
     * This function converts front end data to data that should be set to db
     * @param value value to be converted
     * @return returns converted output that is to be set to db
     */
    public String set(String value);
}
