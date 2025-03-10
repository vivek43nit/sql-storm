<%-- 
    Document   : execute
    Created on : Apr 21, 2015, 1:06:30 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="org.apache.logging.log4j.Logger"%>
<%@page import="org.apache.logging.log4j.LogManager"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.sql.Connection"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>

<%! static Logger logger = LogManager.getLogger("execute.jsp");%>
<jsp:useBean id="req" class="com.vivek.sqlstorm.dto.request.ExecuteRequest" scope="page"/>
<jsp:setProperty name="req" property="*"/> 
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    if(!req.isValid()){
        response.sendError(response.SC_BAD_REQUEST, "Invalid request");
        return;
    }
    
    ArrayList<PreparedStatement> currResults = (ArrayList<PreparedStatement>)session.getAttribute("RS");
    if(currResults == null)
    {
        return;
    }
    logger.debug("Current Cache ResultSet count :"+currResults.size()+"");
    
    Connection con = DatabaseManager.getInstance().getConnection(sessionDetails.getGroup(), req.getDatabase());
    PreparedStatement ps = null;
    logger.info(req.getQuery());
    ps = con.prepareStatement(req.getQuery(), ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
    if(req.getQueryType().equals("S")){
        ps.executeQuery();
        if(!req.getAppend())
        {
            for(PreparedStatement p:currResults){
                p.close();
            }
            currResults.clear();
        }
        currResults.add(ps);
        %>
        <jsp:include page="viewResultSet.jsp">
            <jsp:param name="info" value="${req.info}"></jsp:param>
            <jsp:param name="relation" value="${req.relation}"></jsp:param>
        </jsp:include><%
    }else if(req.getQueryType().equals("U")){
        if(!DatabaseManager.getInstance().isUpdatableConnection(sessionDetails.getGroup(), req.getDatabase())){
            response.sendError(response.SC_FORBIDDEN, "Update Permission is prohibitted for this database");
            return;
        }
        int count = ps.executeUpdate();
        ps.close();
        out.print(count);
    }
%>