<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Dynamic Connection</title>
    <script src="guacamole-common-js/all.min.js"></script>
</head>
<body>
    <div id="display"></div>
    <script>
        var tunnel = new Guacamole.HTTPTunnel('dynamic');
        var client = new Guacamole.Client(tunnel);
        
        document.getElementById('display').appendChild(client.getDisplay().getElement());
        
        client.connect();
        
        window.onunload = function() {
            client.disconnect();
        };
    </script>
</body>
</html>