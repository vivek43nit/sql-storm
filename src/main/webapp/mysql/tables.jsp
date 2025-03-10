<%-- 
    Document   : tables
    Created on : Apr 20, 2015, 5:09:00 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.dto.TableDTO"%>
<%@page import="java.util.Collection"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="org.apache.logging.log4j.Logger"%>
<%@page import="org.apache.logging.log4j.LogManager"%>
<%@page import="java.sql.DatabaseMetaData"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.sql.Connection"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%! static Logger logger = LogManager.getLogger("tables.jsp");%>
<%    
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null || sessionDetails.getGroup() == null){
        return;
    }
    String database = request.getParameter("database");
    if(database == null || "null".equals(database)){
        return;
    }
    Collection<TableDTO> tables = DatabaseManager.getInstance().getTables(sessionDetails.getGroup(), database);
%>
<select class="select table" size="30" autocomplete="off" id="tables" onchange="selectTable(this)">
    <%
    for(TableDTO table : tables){
        %><option value="<%=table.getTableName() %>" data-primary-key="<%=table.getPrimaryKey()==null?"":table.getPrimaryKey() %>" ><%=table.getTableName() %></option><%
    }
%>
</select>
