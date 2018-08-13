<%-- 
    Document   : error
    Created on : Aug 11, 2018, 2:19:57 PM
    Author     : Vivek Kumar <vivek43nit@gmail.com>
--%>

<%@page contentType="text/html" pageEncoding="UTF-8" isErrorPage="true"%>
<% exception.printStackTrace(); %>
<!DOCTYPE html>
{  "error" : "<%=exception%>"}