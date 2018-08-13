<%-- 
    Document   : deleteRow.jsp
    Created on : Apr 23, 2015, 3:55:25 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
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
    String rowIndex = request.getParameter("ri");
    
    if(tableIndex == null || rowIndex == null){
        out.print("Invalid Parameters");
        return;
    }
    
    int ti = Integer.parseInt(tableIndex);
    int ri = Integer.parseInt(rowIndex);
    
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
        
        if(rs.absolute(ri)){
            rs.deleteRow();
            out.print("Row Deleted Successfully");
        }else{
            out.print("Row Deletion Failed");
        }
    }
    catch(Exception e){
        out.print("Error : "+e.getMessage());
    }
%>