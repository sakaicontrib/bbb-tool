<%@ page import="org.sakaiproject.util.ResourceLoader" %>
<%--
    Copyright (c) 2010 onwards - The Sakai Foundation

    Licensed under the Educational Community License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

                http://www.osedu.org/licenses/ECL-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<html
        xmlns="http://www.w3.org/1999/xhtml"
        xml:lang="en"
        lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <script type="text/javascript" language="JavaScript">
        function closeWindow() {
            var objWindow = window.open('', "_self", '');
            objWindow.close();
        }
    </script>
</head>
<body onload="closeWindow()" style="width:99.5%">
<span style="margin:50px auto">
<%! ResourceLoader rl = new ResourceLoader("ToolMessages"); %>
<% rl.setContextLocale(request.getLocale()); %>
<%= "<h2>" + rl.getString("bbb_autoclose1") + "</h2>" %>
<%= "<h3>" + rl.getString("bbb_autoclose2") + "</h3>" %>
<%= "<h2>" + rl.getString("bbb_autoclose3") + "</h2>" %>
</>
</body>
</html>
