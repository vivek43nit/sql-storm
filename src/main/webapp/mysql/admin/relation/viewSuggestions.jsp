<%-- 
    Document   : viewSuggestions
    Created on : Sep 22, 2018, 1:43:49 AM
    Author     : Vivek Kumar <vivek43nit@gmail.com>
--%>
<%@page import="com.vivek.sqlstorm.dto.ReferenceDTO"%>
<%@page import="com.vivek.sqlstorm.dto.ReferenceDTO.Source"%>
<%@page import="java.util.HashSet"%>
<%@page import="org.json.JSONArray"%>
<%@page import="org.json.JSONObject"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnDTO"%>
<%@page import="com.vivek.sqlstorm.dto.TableDTO"%>
<%@page import="java.util.Collection"%>
<%@page import="com.vivek.utils.MultiMap"%>
<%@page import="java.util.Map"%>
<%@page import="com.vivek.sqlstorm.dto.ColumnPath"%>
<%@page import="java.util.HashMap"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Set"%>
<%@page import="com.vivek.sqlstorm.DatabaseManager"%>
<%@page import="com.vivek.sqlstorm.dto.DatabaseDTO"%>
<%@page import="java.util.List"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>  
<%
    String group = request.getParameter("group");
    if (group == null || group.isEmpty()) {
        out.print("group parameter can't be empty");
        return;
    }
    DatabaseManager dbManager = DatabaseManager.getInstance();
    Set<String> databaseNames = dbManager.getDbNames(group);

    MultiMap<String, ColumnPath> primaryKeys = new MultiMap<String, ColumnPath>();

//preparing possible primary keys
    for (String db : databaseNames) {
        DatabaseDTO dbMeta = dbManager.getMetaData(group, db);
        Collection<TableDTO> tables = dbMeta.getTables();
        for (TableDTO table : tables) {
            String pKey = table.getPrimaryKey();
            if (pKey == null) {
                continue;
            }
            pKey = pKey.toLowerCase().replaceAll("_", "");

            List<String> keys = new ArrayList<String>();
            if (!"id".equals(pKey)) {
                keys.add(pKey);
            }
            String tmpTableName = table.getTableName().toLowerCase().replaceAll("_", "");
            keys.add( tmpTableName + pKey);
            if(tmpTableName.endsWith("s")){
                keys.add( tmpTableName.substring(0, tmpTableName.length()-1) + pKey);
            }
            if(tmpTableName.endsWith("ies")){
                keys.add( tmpTableName.substring(0, tmpTableName.length()-3) + "y" + pKey);
            }
            for(String key : keys){
                primaryKeys.put(key, new ColumnPath(db, table.getTableName(), table.getPrimaryKey()));
            }
        }
    }

    JSONObject relations = new JSONObject();

//checking for possible relations
    for (String db : databaseNames) {
        JSONObject dbRelation = new JSONObject();
        DatabaseDTO dbMeta = dbManager.getMetaData(group, db);

        for (TableDTO table : dbMeta.getTables()) {
            JSONObject tableRelation = new JSONObject();

            for (ColumnDTO col : table.getColumns()) {
                if (col.isPrimaryKey()) {
                    continue;
                }
                List<ColumnPath> alreadyExistingRelations = col.getReferTo();
                Set<ColumnPath> newRelations = new HashSet<ColumnPath>(alreadyExistingRelations);

                String key = col.getName().toLowerCase().replaceAll("_", "");
                if (primaryKeys.containsKey(key)) {
                    for(ColumnPath path : primaryKeys.get(key)){
                        ColumnPath clone = new ColumnPath(path);
                        clone.setSource(ReferenceDTO.Source.NEW);
                        newRelations.add(clone);
                    }
                }

                if(!newRelations.isEmpty())
                {
                    tableRelation.put(col.getName(), newRelations);
                }
            }
            if (tableRelation.length() > 0) {
                dbRelation.put(table.getTableName(), tableRelation);
            }
        }
        if (dbRelation.length() > 0) {
            relations.put(db, dbRelation);
        }
    }
%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Relations and Suggestions</title>
        <script src="../../mysql.js?" type="text/javascript"></script>
        <script src="../../loader.js?" type="text/javascript"></script>
        <style>
            .tile:before{
                border-radius: 10px 10px 0px 0px;
            }
            .tile{
                margin: 15px 10px 15px 50px;
                border-radius: 10px;
            }
            .tile.database:before{
                background-color: brown;
            }
            .col-group{
                margin-left: 20px;
                margin-bottom: 10px;
                border-bottom: 1px solid #555;
            }
            .relation{
                margin-left: 30px;
            }
            .type:after{
                display: inline-block;
                border-radius: 5px;
                padding: 1px 10px;
                vertical-align: middle;
                margin-left: 7px;
                box-shadow: 0px 0px 3px blue;
                font-size: 12px;
            }
            .type.NEW:after{
                content: "NEW";
                background-color: blue;
                color: white;
            }
            .type.DB:after{
                content: "DB";
                background-color: green;
            }
            .type.CUSTOM:after{
                content: "CUSTOM";
                background-color: cadetblue;
            }
        </style>
    </head>
    <body>
        <link rel="stylesheet" href="../../mysql.css">

        <% for(String db : relations.keySet()){
            %><div class="tile database" data-title="<%=db%>"><%

                JSONObject dbObject = relations.getJSONObject(db);
                for(String table : dbObject.keySet()){
                    %><div class="tile table-relations" data-title="<%=table%>"><%

                        JSONObject tableObject = dbObject.getJSONObject(table);
                        for(String col : tableObject.keySet()){
                            %>
                            <div class="col-group">
                                <div class="column"><%= col %></div><%

                                JSONArray rels = tableObject.getJSONArray(col);
                                for(int i=0; i<rels.length(); i++){
                                    JSONObject obj = rels.getJSONObject(i);
                                    %>
                                    <div>
                                        <div style="display: inline-block; min-width: 50%">
                                            <input class="relation" type="checkbox" value="" />
                                            <span class="type %>"><%= obj.getString("pathString")%></span>
                                        </div>
                                        <span style="margin-left: 50px;">
                                            Valid if :
                                            <textarea style="vertical-align: middle"><%= (obj.has("conditions"))?obj.optJSONObject("conditions"):""%></textarea>
                                        </span>
                                    </div>
                                    <%
                                }
                            %></div><%
                        }
                    %></div><%
                }
            %></div><%
        }%>
    </body>
</html>