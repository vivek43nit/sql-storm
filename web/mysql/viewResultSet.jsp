<%-- 
    Document   : viewResultSet
    Created on : Apr 20, 2015, 7:29:46 PM
    Author     : Vivek
--%>
<%@page import="mysql.constants.Constants"%>
<%@page import="mysql.SessionDTO"%>
<%@page import="mysql.TableMetaData"%>
<%@page import="org.apache.log4j.Logger"%>
<%@page import="mysql.ReferenceDTO"%>
<%@page import="mysql.MultiMap"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="databasemanager.DatabaseManager"%>
<%@page import="java.sql.Connection"%>
<%@page import="java.sql.ResultSet"%>
<%@page import="java.sql.ResultSetMetaData"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%! static Logger logger = Logger.getLogger("viewResultSet.jsp");%>
<%
    SessionDTO sessionDetails = (SessionDTO) session.getAttribute(Constants.SESSION_DETAILS);
    if (sessionDetails == null) {
        return;
    }
    String tableInfo = request.getParameter("rel");

    ArrayList<PreparedStatement> currResult = (ArrayList<PreparedStatement>) session.getAttribute("RS");
    if (currResult == null) {
        return;
    }
    
    //getting result set for current view to show
    ResultSet rs = currResult.get(currResult.size() - 1).getResultSet();
    ResultSetMetaData metaData = rs.getMetaData();
    
    //getting database and table name for current result set
    String databaseName = metaData.getCatalogName(1);
    String tableName = metaData.getTableName(1);
    
    TableMetaData tableMetaData = TableMetaData.getInstance(sessionDetails.getGroup(), databaseName, tableName);

    MultiMap<String, ReferenceDTO> referedBy = tableMetaData.getReferedBy();
    MultiMap<String, ReferenceDTO> refereTo = tableMetaData.getReferTo();

    int colCount = metaData.getColumnCount();
    if (tableInfo != null && !tableInfo.isEmpty() && !"null".equals(tableInfo)) {
        tableInfo = String.format("%s.%s :: %s", databaseName, tableName, tableInfo);
    } else {
        tableInfo = String.format("%s.%s", databaseName, tableName);;
    }
    
    DatabaseManager dbManager = DatabaseManager.getInstance();
    boolean isEditable = dbManager.isUpdatableConnection(sessionDetails.getGroup(), databaseName);
    
    String tableId = ""+(currResult.size()-1);
%>
<div id="table-<%=tableId%>" class="tile <%=isEditable?"editable":"read-only"%>" data-title="<%=tableInfo %>">
    <div style="margin: 0px;">
    <input data-type="none" type="hidden" class="data" name="db" value="<%=databaseName%>"/>
    <input data-type="none" type="hidden" class="data" name="t" value="<%=tableName%>"/>
    <input data-type="none" type="hidden" class="data" name="table-index" value="<%=tableId%>"/>
    <table class="table">
        <tr>
            <th data-number="<%=currResult.size() - 1%>" class="link">
                <% if(dbManager.isUpdatableConnection(sessionDetails.getGroup(), databaseName)){ %>
                <span onclick="MySQl.addRow(this.parentNode)">Add</span>
                <% } %>
            </th>
            <%for (int i = 1; i <= colCount; i++) {%>
            <th data-nullable="<%=metaData.isNullable(i)%>"><%=metaData.getColumnLabel(i)%></th>
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
                String data = tableMetaData.getFinalValueFromDb(i, rs.getString(i));
                String classes = "";
                if(referedBy.containsKey(metaData.getColumnName(i))){
                    classes = "link referedBy";
                }else if(refereTo.containsKey(metaData.getColumnName(i))){
                    classes = "link referTo";
                }
            %><td name="<%=metaData.getColumnLabel(i)%>" class="<%=classes%>" onclick="MySQl.handleReference(this)"><%=data%></td>
            <%}%>
        </tr>
        <%}%>
    </table>
    </div>
</div>