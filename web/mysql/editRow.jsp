<%-- 
    Document   : editRow
    Created on : Apr 22, 2015, 4:47:31 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
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
        
        ArrayList<String> parameters = new ArrayList<String>();

        if(rs.absolute(ri)){
            for(int i=0; i< metaData.getColumnCount(); i++){
                String cData = request.getParameter(metaData.getColumnLabel(i+1));
                if(!cData.equals(rs.getString(i+1)))
                    rs.updateString(i+1,cData);
            }
            rs.updateRow();
            out.print("Data Updated Successfully");
        }else{
            out.print("Data Update Failed!!!");
        }
    }catch(Exception e){
        out.print("Error : "+e.getMessage());
    }
    
%>