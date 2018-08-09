<%-- 
    Document   : deleteRow.jsp
    Created on : Apr 23, 2015, 3:55:25 PM
    Author     : Vivek
--%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.util.ArrayList"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
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
        ArrayList<String> parameters = new ArrayList<String>();

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