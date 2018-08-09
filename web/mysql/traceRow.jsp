<%-- 
    Document   : traceRow
    Created on : Aug 7, 2018, 4:14:19 AM
    Author     : root
--%>
<%@page import="mysql.ReferenceDTO"%>
<%@page import="mysql.MultiMap"%>
<%@page import="mysql.TableMetaData"%>
<%@page import="org.json.JSONObject"%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%! static Logger logger = Logger.getLogger("traceRow.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    String database = request.getParameter("db");   //database name
    String tableName = request.getParameter("t");   //table Name
    
    if(database == null || tableName == null){
        logger.error("Invalid Parameter");
        return;
    }
    
    String rowStr = request.getParameter("row"); //row data
    JSONObject rowData = null;
    if(rowStr != null && !rowStr.isEmpty()){
        rowData = new JSONObject(rowStr);
    }
    
    TableMetaData tableMetaData = TableMetaData.getInstance(sessionDetails.getGroup(), database, tableName);
    
    MultiMap<String, ReferenceDTO> dereferences = tableMetaData.getReferTo();
    for(String column : dereferences.keySet()){
        %><jsp:include page="getDeReferences.jsp">
            <jsp:param name="db" value="<%=database %>"></jsp:param>
            <jsp:param name="t" value="<%=tableName %>"></jsp:param>
            <jsp:param name="c" value="<%=column%>"></jsp:param>
            <jsp:param name="k" value="<%=rowData.getString(column)%>"></jsp:param>
            <jsp:param name="row" value="<%=rowStr %>"></jsp:param>
        </jsp:include><%
    }
    
    MultiMap<String, ReferenceDTO> references = tableMetaData.getReferedBy();
    for(String column : references.keySet()){
        %><jsp:include page="getReferences.jsp">
            <jsp:param name="db" value="<%=database %>"></jsp:param>
            <jsp:param name="t" value="<%=tableName %>"></jsp:param>
            <jsp:param name="c" value="<%=column%>"></jsp:param>
            <jsp:param name="k" value="<%=rowData.getString(column)%>"></jsp:param>
            <jsp:param name="a" value="1"></jsp:param>
        </jsp:include><%
    }
%>