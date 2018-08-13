<%-- 
    Document   : addRow
    Created on : Apr 23, 2015, 3:37:55 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.util.ArrayList"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    
    String tableIndex = request.getParameter("ti");
    if(tableIndex == null){
        out.print("Invalid Parameters");
        return;
    }
    
    int ti = Integer.parseInt(tableIndex);
    
    ArrayList<PreparedStatement> results = (ArrayList<PreparedStatement>)session.getAttribute("RS");
    try{
        PreparedStatement ps = results.get(ti);
        ResultSet rs = ps.getResultSet();

        ResultSetMetaData metaData = rs.getMetaData();
        
        String databaseName = metaData.getCatalogName(1);
        if(!DatabaseManager.getInstance().isUpdatableConnection(sessionDetails.getGroup(), databaseName)){
            response.sendError(response.SC_FORBIDDEN, "Update Permission is prohibitted for this database");
            return;
        }
    
        ArrayList<String> parameters = new ArrayList<String>();
        rs.moveToInsertRow();
            
        //TODO : use TableDTO for columns details and meta data
        for(int i=0; i< metaData.getColumnCount(); i++){
            rs.updateString(i+1,request.getParameter(metaData.getColumnLabel(i+1)));
        }
        rs.insertRow();
        out.print("Data Added Successfully");
    }
    catch(Exception e){
        out.print("Error : "+e.getMessage());
    }
%>