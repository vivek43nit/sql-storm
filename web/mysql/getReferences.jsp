<%-- 
    Document   : getReferences
    Created on : Apr 22, 2015, 3:42:43 PM
    Author     : Vivek
--%>
<%@page import="mysql.util.CommonFunctions"%>
<%@page import="mysql.relations.MappingTableDto"%>
<%@page import="mysql.TableMetaData"%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="org.json.JSONObject"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.util.ArrayList"%>
<%@page import="mysql.ReferenceDTO"%>
<%@page import="mysql.MultiMap"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%! static Logger logger = Logger.getLogger("getReferences.jsp");%>
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
    
    String currentInfo = CommonFunctions.getName(database, tableName, columnName);
    
    TableMetaData tableMetaData = TableMetaData.getInstance(sessionDetails.getGroup(), database, tableName);
    MultiMap<String,ReferenceDTO> references = tableMetaData.getReferedBy();
    
    if(references == null){
        logger.error("References Not Found");
        return;
    }
    
    ArrayList<ReferenceDTO> referTo = new ArrayList<ReferenceDTO>();
    
    ReferenceDTO current = new ReferenceDTO();
    current.setTableName(tableName);
    current.setColumnName(columnName);
    current.setDatabaseName(database);
    referTo.add(current);
    
    referTo.addAll(references.get(columnName));
    
    boolean flag = false;
    String isAppend = request.getParameter("a");
    if(isAppend != null && isAppend.equals("1")){
        flag = true;
    }
    
    for(ReferenceDTO x : referTo){
        String referenceInfo = CommonFunctions.getName(x.getDatabaseName(), x.getTableName(), x.getColumnName());
        if(referenceInfo.equals(currentInfo)){
            referenceInfo = "SELF";
        }else{
            referenceInfo = currentInfo+"->"+referenceInfo;
        }
        
        TableMetaData currMetaData = TableMetaData.getInstance(sessionDetails.getGroup(), x.getDatabaseName(), x.getTableName());
        String query = "";
        
        //generation extra where queries if any
        if(x.getConditions() != null){
            for(String key : x.getConditions().keySet()){
                query += String.format(" and %s='%s'", key, x.getConditions().get(key));
            }
        }
        
        boolean isResolved = false;
        if(currMetaData.getMappingInfo() != null){
            
            isResolved = true;
            MappingTableDto mapping = currMetaData.getMappingInfo();
            
            String nextColumnName = null;
            if(x.getColumnName().equals(mapping.getFrom())){
                nextColumnName = mapping.getTo();
            }else if(x.getColumnName().equals(mapping.getTo())){
                nextColumnName = mapping.getFrom();
            }else{
                isResolved = false;
            }
            if(isResolved){
                ArrayList<ReferenceDTO> refTo = currMetaData.getReferTo().get(nextColumnName);
                for(ReferenceDTO ref : refTo){
                    String finalQuery = String.format("select * from %s where %s in (select %s from %s where %s='%s' %s)",
                            ref.getReferenceTableName(), ref.getReferenceColumnName(), ref.getColumnName(), ref.getTableName(), x.getColumnName(),value, query);
                    
                    String info = "AUTO-RESOLVE : "+referenceInfo+"->"+
                            CommonFunctions.getName(ref.getReferenceDatabaseName(), ref.getReferenceTableName(), ref.getReferenceColumnName());
                    
                    if(flag)
                    {
                        %><jsp:include page="execute.jsp">
                            <jsp:param name="database" value="<%=ref.getDatabaseName() %>"></jsp:param>
                            <jsp:param name="a" value="1"></jsp:param>
                            <jsp:param name="qt" value="S"></jsp:param> 
                            <jsp:param name="q" value="<%=finalQuery %>"></jsp:param>
                            <jsp:param name="rel" value="<%=info %>"></jsp:param>
                        </jsp:include><%
                    }
                    else{
                        %><jsp:include page="execute.jsp">
                            <jsp:param name="database" value="<%=ref.getDatabaseName() %>"></jsp:param>
                            <jsp:param name="qt" value="S"></jsp:param> 
                            <jsp:param name="q" value="<%=finalQuery %>"></jsp:param>
                            <jsp:param name="rel" value="<%=info %>"></jsp:param>
                        </jsp:include><%
                    }
                    flag = true;
                }
            }
        }
        
        if(!isResolved){
            query = String.format("select * from %s where %s='%s'", x.getTableName(), x.getColumnName(), value)+query;
            if(flag)
            {
                %><jsp:include page="execute.jsp">
                    <jsp:param name="database" value="<%=x.getDatabaseName() %>"></jsp:param>
                    <jsp:param name="a" value="1"></jsp:param>
                    <jsp:param name="qt" value="S"></jsp:param> 
                    <jsp:param name="q" value="<%=query%>"></jsp:param>
                    <jsp:param name="rel" value="<%=referenceInfo %>"></jsp:param>
                </jsp:include><%
            }
            else{
                %><jsp:include page="execute.jsp">
                    <jsp:param name="database" value="<%=x.getDatabaseName() %>"></jsp:param>
                    <jsp:param name="qt" value="S"></jsp:param> 
                    <jsp:param name="q" value="<%=query%>"></jsp:param>
                    <jsp:param name="rel" value="<%=referenceInfo %>"></jsp:param>
                </jsp:include><%
            }
            flag = true;
        }   
    }
%>