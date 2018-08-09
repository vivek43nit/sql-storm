<%-- 
    Document   : viewRelation
    Created on : Aug 7, 2018, 7:32:02 PM
    Author     : root
--%>
<%@page import="mysql.ReferenceDTO"%>
<%@page import="mysql.MultiMap"%>
<%@page import="databasemanager.DatabaseManager"%>
<%@page import="mysql.TableMetaData"%>
<%
    String database = request.getParameter("database");
    String table = request.getParameter("table");
%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>View Relations</title>
        <script src="../../mysql.js?" type="text/javascript"></script>
        <script src="../../loader.js?" type="text/javascript"></script>
        <style>
            .search{
                text-align:center;
                margin-bottom: 30px !important;
            }
            .search input{
                border-radius: 3px;
                border: 0px;
                box-shadow: 0px 0px 3px seagreen;
                line-height: 25px;
                margin: 10px;
                padding: 0px 10px;
            }
        </style>
    </head>
    <body>
        <link rel="stylesheet" href="../../mysql.css">
        <div class="search tile" data-title="Search Relations">
            <form method="GET">
                <span><input type="text" name="database" id="database" value="<%=(database!=null)?database:""%>" placeholder="Database Name"/></span>
                <span><input type="text" name="table" id="table" value="<%=(table!=null)?table:""%>" placeholder="Table Name"/></span>
                <input style="background-color:lightseagreen" type="submit" value="Search"/>
            </form>
        </div>
        <% 
        if(database != null && !database.isEmpty() && table != null && !table.isEmpty()){ 
            TableMetaData tableMetaData = TableMetaData.getInstance(DatabaseManager.getInstance().getGroupNames().iterator().next(), database, table);

            MultiMap<String,ReferenceDTO> referTo = tableMetaData.getReferTo();
        %>
            <div class="tile" data-title="Relations : <%=database+"."+table %>">
                <table class="table">
                    <tr>
                        <th rowspan="2">Source</th>
                        <th colspan="3">From</th>
                        <th colspan="3">To</th>
                        <th rowspan="2">Conditions</th>
                    </tr>
                    <tr>
                        <th>Database</th>
                        <th>Table</th>
                        <th>Column</th>
                        <th>Database</th>
                        <th>Table</th>
                        <th>Column</th>
                    </tr>
                    <% for(String column : referTo.keySet()){
                            for(ReferenceDTO info : referTo.get(column)){ %>
                            <tr>
                                <td><%=info.getSource()%></td>
                                <td><%=info.getDatabaseName() %></td>
                                <td><%=info.getTableName()%></td>
                                <td><%=info.getColumnName()%></td>
                                <td><%=info.getReferenceDatabaseName()%></td>
                                <td><%=info.getReferenceTableName()%></td>
                                <td><%=info.getReferenceColumnName()%></td>
                                <td><%=(info.getConditions()!=null?info.getConditions().toString(4):"")%></td>
                            </tr>
                    <% } } %>
                    <% for(String column : tableMetaData.getReferedBy().keySet()){
                            for(ReferenceDTO info : tableMetaData.getReferedBy().get(column)){ %>
                            <tr>
                                <td><%=info.getSource()%></td>
                                <td><%=info.getDatabaseName() %></td>
                                <td><%=info.getTableName()%></td>
                                <td><%=info.getColumnName()%></td>
                                <td><%=info.getReferenceDatabaseName()%></td>
                                <td><%=info.getReferenceTableName()%></td>
                                <td><%=info.getReferenceColumnName()%></td>
                                <td><%=(info.getConditions()!=null?info.getConditions().toString(4):"")%></td>
                            </tr>
                    <% } } %>
                </table>
            </div>
        <%}%>
    </body>
</html>
