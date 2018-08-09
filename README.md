# sql-storm
A mysql client over browser that where you can navigate from one table to another using all the foreign keys relations in the db and also possible to give custom relation.


Requirement to use sql-storm as a standalone project :

1. configure DatabaseConnection.xml file for the databases to be browsed.
2. configure custom_mapping.json file to mention custom mappings.(Not required if you do not want to mention custom mappings)
3. For logging log4j should be configured. 

Note:
You can either configure these files and deploy on server or you can keep DatabaseConnection and custom_mapping file in /etc/sql-storm folder

For securing sql-storm browsing with a session : 
Check : src/java/filter/SessionFilter.java  for sample code.
