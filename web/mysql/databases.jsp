<%-- 
    Document   : databases
    Created on : Apr 20, 2015, 5:09:00 PM
    Author     : Vivek
--%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="java.sql.DatabaseMetaData"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="databasemanager.DatabaseManager"%>
<%@page import="java.sql.Connection"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        //session should not be null in this page
        return;
    }
    
    String database = request.getParameter("database");
    if(database != null && !database.equals("null")){
        sessionDetails.setDbName(database);
    }
    DatabaseManager dbManager = DatabaseManager.getInstance();
%>
<form autocomplete="off" id="database-form" method="GET" style="display: inline-block">
    <input type="hidden" name="group" value="<%=sessionDetails.getGroup()%>"/>
    <select id="database" name="database" onchange="this.form.submit()" style="overflow: auto;border:0;height: 30px;background-color: darksalmon;">
        <option>Select Database</option>
        <% for(String db : dbManager.getDbNames(sessionDetails.getGroup())){
            %><option value="<%=db%>" <%=(database != null && database.equals(db))?"selected":""%>><%=db%></option><%
        }%>
    </select>
</form>