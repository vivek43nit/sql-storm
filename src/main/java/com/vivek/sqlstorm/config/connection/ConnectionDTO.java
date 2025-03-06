/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vivek.sqlstorm.config.connection;

import lombok.Data;

@Data
public class ConnectionDTO
{
    private String driverClassName;
    private String databaseURL;
    private String user;
    private String password;
    private long id;
    private String group;
    private String dbName;
    private boolean updatable = false;
    private boolean deletable = false;
    private int searchableRowLimit = 30000;
    
    public ConnectionDTO()
    {
        id = -1L;
    }

    public ConnectionDTO(String driverClassName, String databaseURL, String user, String password, long id, String group, String dbName) {
        this.driverClassName = driverClassName;
        this.databaseURL = databaseURL;
        this.user = user;
        this.password = password;
        this.id = id;
        this.group = group;
        this.dbName = dbName;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (this.group != null ? this.group.hashCode() : 0);
        hash = 47 * hash + (this.dbName != null ? this.dbName.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ConnectionDTO other = (ConnectionDTO) obj;
        if ((this.group == null) ? (other.group != null) : !this.group.equals(other.group)) {
            return false;
        }
        if ((this.dbName == null) ? (other.dbName != null) : !this.dbName.equals(other.dbName)) {
            return false;
        }
        return true;
    }
    
}
