<%-- 
    Document   : getDeReferences
    Created on : Apr 23, 2015, 5:48:54 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.utils.DBHelper"%>
<%@page import="java.util.List"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnPath"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnDTO"%>
<%@page import="com.vivek.sqlstorm.dto.DatabaseDTO"%>
<%@page import="com.vivek.sqlstorm.dto.TableDTO"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="java.util.Iterator"%>
<%@page import="org.json.JSONArray"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.util.ArrayList"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%! static Logger logger = Logger.getLogger("getDeReferences.jsp");%>
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
    
    ColumnDTO colMetaData = DatabaseManager.getInstance()
            .getMetaData(sessionDetails.getGroup(), req.getDatabase())
            .getTableMetaData(req.getTable())
            .getColumnMetaData(req.getColumn());
    
    if(colMetaData.getReferTo().isEmpty()){
        logger.error("References Not Found");
        out.print("No references found");
        return;
    }
//    
//    ColumnPath selfPath = new ColumnPath(req.getDatabase(), req.getTable(), req.getColumn());
//    
    List<ColumnPath> referToList = colMetaData.getReferTo();;
//    
//    can not include self in back referencing because where clause for self will fetch non unique rows
//    if(req.getIncludeSelf()){
//        referToList = new ArrayList<ColumnPath>();
//        referToList.add(selfPath);
//        referToList.addAll(colMetaData.getReferTo());
//    }else{
//        referToList = 
//    }
//    
    for(ColumnPath referTo : referToList){
        
        //checking for this relation is valid or not if condition exists
        if(referTo.getConditions() != null && data != null){
            if(!DBHelper.isReferToConditionMatch(referTo.getConditions(), data)){
                logger.error("Conditions Not matched : "+referTo.getConditions()+" == "+data);
                continue;
            }
        }
        String query = String.format("select * from %s where %s='%s'", referTo.getTable(), referTo.getColumn(), value); 
        %><jsp:include page="execute.jsp">
            <jsp:param name="database" value="<%=referTo.getDatabase()%>"></jsp:param>
            <jsp:param name="append" value="${req.append}"></jsp:param>
            <jsp:param name="queryType" value="S"></jsp:param> 
            <jsp:param name="query" value="<%=query%>"></jsp:param>
            <jsp:param name="info" value="<%=referTo.getColumn()%>"></jsp:param>
            <jsp:param name="relation" value="referTo"></jsp:param>
        </jsp:include><%
        req.setAppend(true);
    }
%>