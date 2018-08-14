<%-- 
    Document   : viewResultSet
    Created on : Apr 20, 2015, 7:29:46 PM
    Author     : Vivek
--%>
<%@page import="java.util.Collection"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnDTO"%>
<%@page import="java.util.List"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.dto.TableDTO"%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="com.vivek.sqlstorm.dto.SessionDTO"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.sql.Connection"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%! static Logger logger = Logger.getLogger("viewResultSet.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO) session.getAttribute(Constants.SESSION_DETAILS);
    if (sessionDetails == null) {
        return;
    }
    ArrayList<PreparedStatement> currResult = (ArrayList<PreparedStatement>) session.getAttribute("RS");
    if (currResult == null) {
        return;
    }
    
    String tableInfo = request.getParameter("info");
    String relation = request.getParameter("relation");
    
    //getting result set for current view to show
    ResultSet rs = currResult.get(currResult.size() - 1).getResultSet();
    ResultSetMetaData metaData = rs.getMetaData();
    
    //getting database and table name for current result set
    String databaseName = metaData.getCatalogName(1);
    String tableName = metaData.getTableName(1);
    
    DatabaseManager dbManager = DatabaseManager.getInstance();
    
    TableDTO tableMetaData = dbManager.getMetaData(sessionDetails.getGroup(), databaseName).getTableMetaData(tableName);

//    if (tableInfo != null && !tableInfo.isEmpty() && !"null".equals(tableInfo)) {
//        tableInfo = String.format("%s.%s :: %s", databaseName, tableName, tableInfo);
//    } else {
//        tableInfo = ;;
//    }
    
    boolean isEditable = dbManager.isUpdatableConnection(sessionDetails.getGroup(), databaseName);
    
    String tableId = ""+(currResult.size()-1);
    int colCount = metaData.getColumnCount();
    Collection<ColumnDTO> columns = tableMetaData.getColumns();
%>
<div id="table-<%=tableId%>" class="table-container  <%=relation%>">
    <div class="tableHeader <%=isEditable?"editable":"read-only"%>">
        <span class="tableName"><%= databaseName%> -> <span style="font-style: italic;font-family: monospace;font-weight: bold"><%=tableName%></span></span>
        <span class="relationInfo"><%=tableInfo %></span>
    </div>
    <div style="margin: 0px;border-radius: 0px">
    <input data-type="none" type="hidden" class="data" name="database" value="<%=databaseName%>"/>
    <input data-type="none" type="hidden" class="data" name="table" value="<%=tableName%>"/>
    <input data-type="none" type="hidden" class="data" name="table-index" value="<%=tableId%>"/>
    <table class="table">
        <tr>
            <th data-number="<%=currResult.size() - 1%>" class="link">
                <% if(isEditable){ %>
                <span onclick="MySQl.addRow(this.parentNode)">Add</span>
                <% } %>
            </th>
            <%for (int i = 1; i <= colCount; i++) {
                ColumnDTO colMetaData = tableMetaData.getColumnMetaData(metaData.getColumnLabel(i));
                String _class = "";
                if(colMetaData.isIndexed()){
                    _class += " indexed filterable";
                }
                if(colMetaData.isPrimaryKey()){
                    _class += " primaryKey";
                }
                %><th class="<%=_class %>" data-nullable="<%=metaData.isNullable(i)%>"><%=metaData.getColumnLabel(i)%></th>
            <%}%>
        </tr>
        <% while (rs.next()) { %>
        <tr>
            <td>
                <% if(dbManager.isDeletableConnection(sessionDetails.getGroup(), databaseName)){ %>
                    <span class='link' onclick='MySQl.deleteRow(this.parentNode)'>Delete</span>&nbsp;
                <%} %>
                <% if(dbManager.isUpdatableConnection(sessionDetails.getGroup(), databaseName)){ %>
                    <span class='link' onclick='MySQl.editRow(this.parentNode)'>Edit</span>&nbsp;
                <% } %>
                <span class='link' onclick='MySQl.traceRow("<%=databaseName%>", "<%=tableName%>", this.parentNode)'>Trace</span>
            </td>
            <%
            for (int i = 1; i <= colCount; i++) {
                String colName = metaData.getColumnName(i);
                String data = tableMetaData.getColumnMetaData(colName).getFinalValueFromDb(rs.getString(i));
                String classes = "";
                if(tableMetaData.getColumnMetaData(colName).getReferencedBy().size() > 0){
                    classes = "link referedBy";
                }else if(tableMetaData.getColumnMetaData(colName).getReferTo().size() > 0){
                    classes = "link referTo";
                }
                %><td name="<%=colName %>" class="<%=classes%>" onclick="MySQl.handleReference(this)"><%=data%></td>
            <%}%>
        </tr>
        <%}%>
    </table>
    </div>
</div>