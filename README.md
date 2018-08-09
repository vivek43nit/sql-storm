# sql-storm
A mysql client over browser that where you can navigate from one table to another using all the foreign keys relations in the db and also possible to give custom relation.

Components Required to Start on server
1. apache-tomcat 
    See the tomcat installation reference for this.

How to Run :
    Required : 
        1. copy src/java/DatabaseConnection.xml to /etc/sql-storm/DatabaseConnection.xml file for the databases to be browsed and configure your db connections in this file
    
    Optional :
        2. copy src/java/custom_mapping.json to /etc/sql-storm/custom_mapping.json file to mention custom mappings.(Not required if you do not want to mention custom mappings)
        3. For securing sql-storm browsing with a session : 
            Check : src/java/filter/SessionFilter.java  for sample code.
    
    Now just deploy dist/sql-storm.war file into apache tomcat server.



