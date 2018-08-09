<%-- 
    Document   : execute
    Created on : Apr 21, 2015, 1:06:30 PM
    Author     : Vivek
--%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="databasemanager.DatabaseManager"%>
<%@page import="java.sql.Connection"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%! static Logger logger = Logger.getLogger("execute.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    
    String query = request.getParameter("q");
    String queryType = request.getParameter("qt");
    String database = request.getParameter("database");
    String relation = request.getParameter("rel");
    
    boolean appendResult = false;
    if(request.getParameter("a") != null){
        appendResult = true;
    }    
    if(query == null || queryType == null)
        return;
    
    ArrayList<PreparedStatement> currResults = (ArrayList<PreparedStatement>)session.getAttribute("RS");
    if(currResults == null)
    {
        return;
    }
    logger.debug(currResults.size()+"");
    
    Connection con = DatabaseManager.getInstance().getConnection(sessionDetails.getGroup(), database);
    PreparedStatement ps = null;
    logger.info(query);
    ps = con.prepareStatement(query,ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_UPDATABLE);
    if(queryType.equals("S")){
        ps.executeQuery();
        if(!appendResult)
        {
            for(PreparedStatement p:currResults){
                p.close();
            }
            currResults.clear();
        }
        currResults.add(ps);
        %>
        <jsp:include page="viewResultSet.jsp">
            <jsp:param name="rel" value="<%=relation%>"></jsp:param>
        </jsp:include><%
    }else if(queryType.equals("U")){
        int count = ps.executeUpdate();
        ps.close();
        out.print(count);
    }
%>