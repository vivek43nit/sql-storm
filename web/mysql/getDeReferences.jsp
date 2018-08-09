<%-- 
    Document   : getDeReferences
    Created on : Apr 23, 2015, 5:48:54 PM
    Author     : Vivek
--%>
<%@page import="mysql.TableMetaData"%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.util.ArrayList"%>
<%@page import="mysql.ReferenceDTO"%>
<%@page import="mysql.MultiMap"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%! static Logger logger = Logger.getLogger("getDeReferences.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO)session.getAttribute(Constants.SESSION_DETAILS);
    if(sessionDetails == null){
        return;
    }
    String database = request.getParameter("db");   //database name
    String tableName = request.getParameter("t");   //table Name
    String columnName = request.getParameter("c");  //column Name
    String value = request.getParameter("k");       //key
    
    if(tableName == null || columnName == null || value == null){
        logger.error("Invalid Parameter");
        return;
    }
    
    String rowStr = request.getParameter("row"); //conditions values
    JSONObject rowData = null;
    if(rowStr != null && !rowStr.isEmpty()){
        rowData = new JSONObject(rowStr);
    }
    
    TableMetaData tableMetaData = TableMetaData.getInstance(sessionDetails.getGroup(), database, tableName);
    MultiMap<String,ReferenceDTO> dereferences = tableMetaData.getReferTo();
    
    if(dereferences == null){
        logger.error("References Not Found");
        return;
    }
    
    ArrayList<ReferenceDTO> referTo = dereferences.get(columnName);
    boolean flag = false;
    
    for(ReferenceDTO x : referTo){
        if(x.getConditions() != null && rowData != null){
            JSONObject conditions = x.getConditions();
            boolean isMatched = true;
            for(String key : conditions.keySet()){
                if(!conditions.getString(key).equals(rowData.getString(key))){
                    isMatched = false;
                    break;
                }
            }
            if(!isMatched){
                continue;
            }else{
                logger.error("Conditions Not matched : "+x.getConditions()+" == "+conditions);
            }
        }
        String query = String.format("select * from %s where %s='%s'", x.getReferenceTableName(), x.getReferenceColumnName(), value); 
        if(flag)
        {
            %><jsp:include page="execute.jsp">
                <jsp:param name="database" value="<%=x.getReferenceDatabaseName()%>"></jsp:param>
                <jsp:param name="a" value="1"></jsp:param>
                <jsp:param name="qt" value="S"></jsp:param> 
                <jsp:param name="q" value="<%=query%>"></jsp:param>
                <jsp:param name="rel" value="<%=x.getReferenceColumnName() %>"></jsp:param>
            </jsp:include><%
        }
        else{
            %><jsp:include page="execute.jsp">
                <jsp:param name="database" value="<%=x.getReferenceDatabaseName()%>"></jsp:param>
                <jsp:param name="qt" value="S"></jsp:param> 
                <jsp:param name="q" value="<%=query%>"></jsp:param>
                <jsp:param name="rel" value="<%=x.getReferenceColumnName() %>"></jsp:param>
            </jsp:include><%
        }
        flag = true;
    }
%>