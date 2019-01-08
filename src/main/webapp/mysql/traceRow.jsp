<%-- 
    Document   : traceRow
    Created on : Aug 7, 2018, 4:14:19 AM
    Author     : root
--%>
<%@page import="com.vivek.sqlstorm.dto.ColumnDTO"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.dto.TableDTO"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%! static Logger logger = Logger.getLogger("traceRow.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    
    
    String database = request.getParameter("database");   //database name
    String tableName = request.getParameter("table");   //table Name
    
    if(database == null || tableName == null){
        response.sendError(response.SC_BAD_REQUEST, "Invalid request");
        return;
    }
    
    String ref_row_limit = request.getParameter("refRowLimit");
    if(ref_row_limit == null || ref_row_limit.isEmpty()){
        ref_row_limit = ""+Constants.DEFAULT_REFERENCES_ROWS_LIMIT;
    }
    
    String rowStr = request.getParameter("row"); //row data
    JSONObject data = null;
    if(rowStr != null && !rowStr.isEmpty()){
        data = new JSONObject(rowStr);
    }
    
    TableDTO tableMetaData = DatabaseManager.getInstance().getMetaData(sessionDetails.getGroup(), database).getTableMetaData(tableName);
    
    boolean isAppend = false;
    for(ColumnDTO column : tableMetaData.getColumns()){
        if(column.getReferTo().isEmpty()){
            continue;
        }
        %><jsp:include page="getDeReferences.jsp">
            <jsp:param name="database" value="<%=database %>"></jsp:param>
            <jsp:param name="table" value="<%=tableName %>"></jsp:param>
            <jsp:param name="column" value="<%=column.getName() %>"></jsp:param>
            <jsp:param name="row" value="<%=rowStr %>"></jsp:param>
            <jsp:param name="append" value="<%=isAppend %>"></jsp:param>
            <jsp:param name="includeSelf" value="false"></jsp:param>
            <jsp:param name="refRowLimit" value="<%=ref_row_limit%>"></jsp:param>
        </jsp:include><%
        isAppend = true;
    }
    
    boolean includeSelf = true;
    for(ColumnDTO column : tableMetaData.getColumns()){
        if(column.getReferencedBy().isEmpty()){
            continue;
        }
        %><jsp:include page="getReferences.jsp">
            <jsp:param name="database" value="<%=database %>"></jsp:param>
            <jsp:param name="table" value="<%=tableName %>"></jsp:param>
            <jsp:param name="column" value="<%=column.getName() %>"></jsp:param>
            <jsp:param name="row" value="<%=rowStr %>"></jsp:param>
            <jsp:param name="append" value="<%=isAppend %>"></jsp:param>
            <jsp:param name="includeSelf" value="<%=includeSelf %>"></jsp:param>
            <jsp:param name="refRowLimit" value="<%=ref_row_limit%>"></jsp:param>
        </jsp:include><%
        includeSelf = false;
    }
%>