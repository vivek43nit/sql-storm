/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package databasemanager;

public class DBConnectionDTO
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

    public DBConnectionDTO()
    {
        id = -1L;
    }

    public DBConnectionDTO(String driverClassName, String databaseURL, String user, String password, long id, String group, String dbName) {
        this.driverClassName = driverClassName;
        this.databaseURL = databaseURL;
        this.user = user;
        this.password = password;
        this.id = id;
        this.group = group;
        this.dbName = dbName;
    }
    
    

    public void setDefaultDatabase(boolean isDefault)
    {
        isDefault = isDefault;
    }

    public void setDatabaseURL(String databaseURL)
    {
        this.databaseURL = databaseURL;
    }

    public String getDatabaseURL()
    {
        return databaseURL;
    }

    public String getDriverClassName()
    {
        return driverClassName;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isUpdatable() {
        return updatable;
    }

    public void setUpdatable(boolean updatable) {
        this.updatable = updatable;
    }

    public boolean isDeletable() {
        return deletable;
    }

    public void setDeletable(boolean deletable) {
        this.deletable = deletable;
    }
    
    
}
