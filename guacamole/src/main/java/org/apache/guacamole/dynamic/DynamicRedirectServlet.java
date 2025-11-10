package org.apache.guacamole.dynamic;

import org.apache.guacamole.GuacamoleException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

import javax.servlet.http.HttpSession;
import java.io.IOException;

public class DynamicRedirectServlet extends HttpServlet {

    @Inject
    private DynamicConnectionService dynamicService;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        try {
            String protocol = request.getParameter("protocol");
            String hostname = request.getParameter("hostname");
            String port = request.getParameter("port");
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            
            if (protocol != null && hostname != null) {
                // 创建动态连接配置
                GuacamoleConfiguration config = new GuacamoleConfiguration();
                config.setProtocol(protocol);
                config.setParameter("hostname", hostname);
                config.setParameter("port", port != null ? port : getDefaultPort(protocol));
                
                if (username != null) config.setParameter("username", username);
                if (password != null) config.setParameter("password", password);

                // 创建连接ID
                String connectionId = dynamicService.createDynamicConnection(config);
                
                // 存储到会话（可选，用于后续验证）
                HttpSession session = request.getSession();
                session.setAttribute("dynamicConnectionId", connectionId);
                
                // 重定向到干净的URL
                String cleanUrl = request.getContextPath() + "/#/client/" + connectionId;
                System.out.println("🔗 Redirecting to clean URL: " + cleanUrl);
                response.sendRedirect(cleanUrl);
                
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameters");
            }
            
        } catch (Exception e) {
            throw new ServletException("Failed to create dynamic connection", e);
        }
    }
    
    private String getDefaultPort(String protocol) {
        switch (protocol.toLowerCase()) {
            case "ssh": return "22";
            case "rdp": return "3389";
            case "vnc": return "5900";
            default: return "22";
        }
    }
}