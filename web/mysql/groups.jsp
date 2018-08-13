<%-- 
    Document   : tables
    Created on : Apr 20, 2015, 5:09:00 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.sql.DatabaseMetaData"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.sql.Connection"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%! static Logger logger = Logger.getLogger("groups.jsp");%>
<%
    String groupName = request.getParameter("group");
    logger.info("Group Name :"+groupName);
    if(groupName != null && !groupName.equals("null")){
        SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
        if(sessionDetails == null){
            sessionDetails = new SessionDTO();
            session.setAttribute(Constants.SESSION_DETAILS, sessionDetails);
        }
        sessionDetails.setGroup(groupName);
    }else{
        session.removeAttribute(Constants.SESSION_DETAILS);
    }
    DatabaseManager dbManager = DatabaseManager.getInstance();
%>
<form autocomplete="off" id="group-form" method="GET" style="display: inline-block">
    <select id="group" name="group" onchange="this.form.submit()" style="overflow: auto;border:0;height: 30px;background-color: darksalmon;">
        <option>Select Group</option>
        <% for(String group : dbManager.getGroupNames()){
            %><option value="<%=group%>" <%=(groupName != null && groupName.equals(group))?"selected":""%>><%=group%></option><%
        }%>
    </select>
</form>