<%-- 
    Document   : addRow
    Created on : Apr 23, 2015, 3:37:55 PM
    Author     : Vivek
--%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.util.ArrayList"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
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
        ArrayList<String> parameters = new ArrayList<String>();

        rs.moveToInsertRow();

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