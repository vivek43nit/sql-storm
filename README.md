# sql-storm
A mysql client over browser, where you can navigate from one table to another using all the foreign keys relations in the db and also possible to give custom relation.

#### Components Required to Start :
* **apache-tomcat**
    See the tomcat [installation reference](https://tomcat.apache.org/tomcat-9.0-doc/setup.html) for this.

#### Generating war file from source code :
```sh
$ git clone https://github.com/vivek43nit/sql-storm.git
$ cd sql-storm
$ mvn clean install
```
After successful completion of maven install task, **war** file will be generated inside **target** folder.

#### Configuration Required before application deployment :
    Compulsory : 
        1. copy src/main/resources/DatabaseConnection.xml to /etc/sql-storm/DatabaseConnection.xml file for the databases to be browsed and configure your db connections in this file.

    Optional :
        1. copy src/main/resources/custom_mapping.json to /etc/sql-storm/custom_mapping.json file to mention custom mappings.(Not required if you do not want to mention custom mappings)
        2. For securing sql-storm browsing with a session : 
            Check : src/java/filter/SessionFilter.java  for sample code.
<br/>

#### Deploying the Web Application :
Now deploy the war file into apache tomcat server. 
Check [tomcat deployment guide](https://tomcat.apache.org/tomcat-9.0-doc/deployer-howto.html) for more info.
