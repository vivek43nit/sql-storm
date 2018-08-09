<%-- 
    Document   : tables
    Created on : Apr 20, 2015, 5:09:00 PM
    Author     : Vivek
--%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.sql.DatabaseMetaData"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="databasemanager.DatabaseManager"%>
<%@page import="java.sql.Connection"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%! static Logger logger = Logger.getLogger("tables.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null || sessionDetails.getGroup() == null || sessionDetails.getDbName() == null){
        return;
    }
    
    Connection con = DatabaseManager.getInstance().getConnection(sessionDetails.getGroup(), sessionDetails.getDbName());
    
    //if connection db is not setted in connection then set here
    if(con.getCatalog() == null || con.getCatalog().isEmpty()){
        con.setCatalog(sessionDetails.getDbName());
    }

    PreparedStatement ps = con.prepareStatement("show tables");
    ResultSet rs = ps.executeQuery();
    int colCount = rs.getMetaData().getColumnCount();
    int rowCount = rs.getFetchSize();
%>
<select class="select table" size="30" autocomplete="off" id="tables" onchange="selectTable(this)">
    <%
    while(rs.next()){
        for(int i=1; i<=colCount; i++){
            %><option value="<%=rs.getString(i)%>" ><%=rs.getString(i)%></option><%
        }
    }
%>
</select>
<%
    rs.close();
%>