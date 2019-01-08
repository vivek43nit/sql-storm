<%-- 
    Document   : layout
    Created on : Apr 20, 2015, 5:08:34 PM
    Author     : Vivek
--%>
<%@page import="com.vivek.sqlstorm.constants.Constants"%>
<%@page import="java.sql.PreparedStatement"%>
<%@page import="java.util.ArrayList"%>
<%@page contentType="text/html" pageEncoding="UTF-8" errorPage="error.jsp"%>
<%
    ArrayList<PreparedStatement> list = (ArrayList<PreparedStatement>)session.getAttribute("RS");
    if(list != null){
        list.clear();
    }else{
        session.setAttribute("RS", new ArrayList<PreparedStatement>());
    }
    String group = request.getParameter("group");
    String database = request.getParameter("database");
%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta http-equiv="Pragma" content="no-cache"/>
        <meta http-equiv="Cache-Control" content="no-cache"/>
        <meta http-equiv="Expires" content="0"/>
        <title>MySql Browser</title>
        <script src="loader.js?" type="text/javascript"></script>
        <script src="mysql.js?" type="text/javascript"></script>
        <style>
            .body{
                width: 100%;
                height: 100%;
                margin: 0;
                padding: 0;
                font-size: 12px;
            }
            .topSegment{
                width: 100%;
                height: 30px;
                border: 1px solid #ccc;
                vertical-align: top;
            }
            .leftSegment{
                width: 15%;
                display: inline-block;
                height: 100%;
                max-height: 100%;
                min-width: 200px;
            }
            .dataSegment{
                vertical-align: top;
                max-height: 100%;
                width: 80%;
                height: 100%;
                padding : 10px;
                display: inline-block;
            }
            .filterSegment{
                width: 100%;
                height: 100%;
                min-height: 300px;
                vertical-align: top;
/*                float: left;*/
                margin-right: 10px;
            }
            .dataSegment div{
                width: auto;
                height: 100%;
                max-height: 100%;
                overflow: auto;
                overflow-y: auto;
                padding: 0px;
                border-radius: 10px 10px 5px 5px;
                margin-bottom: 40px;
            }
            #divDialog{
                display: none;
                position: absolute;
                z-index: 50;
                height: 100%;
                width: 100%;
            }
            .blur{
                position: absolute;
                height: 100%;
                width: 100%;
                opacity: 0.5;
                background-color: black;
            }
            .dialogData{
                position: relative;
                width:80%;
                margin: 0 auto;
                background-color: white;
                min-height: 50%;
                margin-top:10%;
            }
            .loading{
                display: none;
                position: absolute;
                z-index: 99;
                height: 100%;
                width: 100%;
            }
            .loadingIcon{
                position: relative;
                /*background-color: white;*/
                width: 30px;
                height: 30px;
                background-image: url(loading.gif);
                margin: 0 auto;
                margin-top: 20%;
            }
            #dialogData{
                width:60%;
                display: inline-block;
            }
            .select{
                overflow: auto; border:0; background-color: darksalmon; height: 30px;
            }
            .select.table{
                height: 350px;
                padding: 5px;
                background-color: white;
            }
            .select.table option{
                height: 14px;
                padding: 3px 3px;
                border-bottom: 1px solid seagreen;
            }
            .select.table select:-internal-list-box option:checked{
                background-color: aliceblue !important;
            }
            .select.table option:checked,option:selected{
                background-color: aliceblue !important;
            }
            .select.table option:hover{
                background-color: lightslategrey;
                box-shadow: 0px 0px 2px seagreen;
                cursor: pointer;
            }
            .searchBtn{
                background-color: lightseagreen;
                border-radius: 3px;
                border: 0px;
                box-shadow: 0px 0px 3px seagreen;
                line-height: 25px;
                margin: 5px;
                padding: 0px 10px;
            }
        </style>
    </head>
    <body class="body">
        <link rel="stylesheet" href="mysql.css">
        <div class="loading" id="loading">
            <div class="blur" style="background-color: white"></div>
            <div class="loadingIcon"></div>
        </div>
        <div id="divDialog">
            <div class="blur"></div>
            <div class="dialogData">
                <div>
                    <div style="float: right;cursor: pointer;position: relative;" class="link" onclick="RH.g('dialogMessage').innerHTML='';RH.g('divDialog').style.display='none';">Close</div>
                    <div id="dialogMessage" style="color: red;text-align: center;padding-top: 10px;"></div>
                </div>
                <div id="dialogData"></div>
                <div class="tile" data-title="Converter" style="display: inline-block;width: 30%;position: absolute;">
                    <table style="margin-bottom: 5px;">
                        <tr>
                            <td>IP in String :</td>
                            <td><input type="text" id="ipStr" onblur="ip2num(this,'ipLong')"/></td>
                        </tr>
                        <tr>
                            <td>IP in Long :</td>
                            <td><input type="text" id="ipLong" onblur="num2ip(this,'ipStr')"/></td>
                        </tr>
                        <tr>
                            <td>Current Time on Server : </td>
                            <td id="cTime" style="cursor: pointer;background-color: orange;" onmouseover="clearInterval(cTimer)" onmouseout="cTimer = setInterval(function(){getTimeInMills('cTime')},1000)"></td>
                        </tr>
                        <tr>
                            <td>Date as String in Local Time : </td>
                            <td><input type="text" id="dateStr" onblur="RH.g('dateLong').value = new Date(this.value).getTime();" placeholder="yyyy/mm/dd hh:mm:ss" value=""/></td>
                        </tr>
                        <tr>
                            <td>Date in Long in UTC:</td>
                            <td><input type="text" id="dateLong" onblur="tmp = new Date(eval(this.value));RH.g('dateStr').value = tmp.getFullYear()+'/'+(tmp.getMonth()+1)+'/'+tmp.getDate()+' '+tmp.getHours()+':'+tmp.getMinutes()+':'+tmp.getSeconds()"/></td>
                        </tr>    
                    </table>         
                    <script>
                        var date = new Date();
                        var diffInMills = date.getTime() - <%=System.currentTimeMillis()%>;
                        delete(date);
                        var cTimer = setInterval(function(){getTimeInMills('cTime')},1000);
                    </script>
                </div>
            </div>
        </div>
        <div class="messDiv" id="messageDiv"></div>
        <div class="body">
            <div class="topSegment">
                <jsp:include page="groups.jsp">
                    <jsp:param name="group" value="<%=group%>"></jsp:param>
                </jsp:include>
                <jsp:include page="databases.jsp">
                    <jsp:param name="database" value="<%=database%>"></jsp:param>
                </jsp:include>
                <div class="topSegmentFilters">
                    <span>Range : <input class="input" type="text" value="0" id="limitStart" placeholder="Start"/></span> -> <span><input class="input" type="text" value="10" id="limitEnd" placeholder="End"/></span>
                </div>
                <div class="topSegmentFilters">
                    <span>Order By : <input class="input" style="width: 100px;" type="text" value="" id="orderBy"/></span>
                    <span>
                        <select class="select" id="order">
                            <option value="DESC">DESC</option>
                            <option value="ASC">ASC</option>
                        </select>
                    </span>
                </div>
                <div class="topSegmentFilters">
                    <span>References Rows Limit: <input class="input" type="text" value="<%=Constants.DEFAULT_REFERENCES_ROWS_LIMIT%>" id="refRowLimit"/></span>
                </div>
                <span style="float: right"><a style="color: seagreen;line-height: 30px;padding: 5px;" href="admin/relation/viewRelation.jsp" target="_blank">View Relations</a></span>
            </div>
                <div style="height: 100%; width: 100%; margin: 0px; padding: 0px">
                    <div class="leftSegment">
                        <div class="tile" data-title="Tables" style="margin: 0px;height: 380px;">
                            <jsp:include page="tables.jsp">
                                <jsp:param name="database" value="<%=database%>"></jsp:param>
                            </jsp:include>
                        </div>
                        <div class="filterSegment tile" data-title="Filters">
                            <div id="filters">
                                Please Click on a Table
                            </div>
                            <script>
                                function ok() {
                                    selectTable(RH.g('tables'), false);
                                }
                            </script>
                            <input class="searchBtn" type="button" value="Search" onclick="ok()"/> 
                        </div>
                    </div>
                    <div class="dataSegment" id="rightSegment">
                    </div>
                </div>
            
        </div>
    </body>
</html>
