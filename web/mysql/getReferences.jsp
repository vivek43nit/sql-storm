<%-- 
    Document   : getReferences
    Created on : Apr 22, 2015, 3:42:43 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.dto.request.ExecuteRequest"%>
<%@page import="com.vivek.sqlstorm.dto.MappingTableDto"%>
<%@page import="com.vivek.sqlstorm.utils.DBHelper"%>
<%@page import="com.vivek.sqlstorm.dto.TableDTO"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnPath"%>
<%@page import="java.util.List"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnDTO"%>
<%@page import="com.vivek.utils.CommonFunctions"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="org.json.JSONArray"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.util.ArrayList"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%! static Logger logger = Logger.getLogger("getReferences.jsp");%>
<jsp:useBean id="req" class="com.vivek.sqlstorm.dto.request.GetRelationsRequest"/>
<jsp:setProperty name="req" property="*"/> 
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    logger.debug("request:"+req);
    if(!req.isValid()){
        response.sendError(response.SC_BAD_REQUEST, "Invalid request");
        return;
    }
    
    //row data
    String rowStr = request.getParameter("row"); 
    JSONObject data = null;
    if(rowStr != null && !rowStr.isEmpty()){
        data = new JSONObject(rowStr);
        req.setData(data);
    }
    String value = data.getString(req.getColumn());
    
    TableDTO tableMetaData = DatabaseManager.getInstance()
            .getMetaData(sessionDetails.getGroup(), req.getDatabase())
            .getTableMetaData(req.getTable());
    
    ColumnDTO colMetaData = tableMetaData.getColumnMetaData(req.getColumn());
    
    if(colMetaData.getReferencedBy().isEmpty()){
        logger.error("References Not Found");
        out.print("No references found");
        return;
    }
    
    ColumnPath selfPath = new ColumnPath(req.getDatabase(), req.getTable(), req.getColumn());
    
    List<ColumnPath> referencedByList = null;
    if(req.getIncludeSelf()){
        referencedByList = new ArrayList<ColumnPath>();
        referencedByList.add(selfPath);
        referencedByList.addAll(colMetaData.getReferencedBy());
    }else{
        referencedByList = colMetaData.getReferencedBy();
    }
    
    for(ColumnPath referencedBy : referencedByList){
        for(ExecuteRequest r : DBHelper.getExecuteRequestsForReferedByReq(sessionDetails.getGroup(), selfPath, referencedBy, value, req.isAppend())){
            %><jsp:include page="execute.jsp">
                <jsp:param name="database" value="<%=r.getDatabase() %>"></jsp:param>
                <jsp:param name="queryType" value="S"></jsp:param> 
                <jsp:param name="query" value="<%=r.getQuery() %>"></jsp:param>
                <jsp:param name="info" value="<%=r.getInfo() %>"></jsp:param>
                <jsp:param name="append" value="<%=r.getAppend() %>"></jsp:param>
                <jsp:param name="relation" value="<%=r.getRelation() %>"></jsp:param>
            </jsp:include><%
        }
    }
%>