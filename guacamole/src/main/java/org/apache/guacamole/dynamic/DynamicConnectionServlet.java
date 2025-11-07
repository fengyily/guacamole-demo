package org.apache.guacamole.dynamic;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleResourceNotFoundException;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.apache.guacamole.servlet.GuacamoleHTTPTunnelServlet;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class DynamicConnectionServlet extends GuacamoleHTTPTunnelServlet {
    
    @Inject
    private DynamicConnectionService dynamicService;
    
    @Override
    protected GuacamoleTunnel doConnect(HttpServletRequest request) throws GuacamoleException {
        // 从会话获取连接ID
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new GuacamoleClientException("No active session");
        }
        
        String connectionId = (String) session.getAttribute("dynamicConnectionId");
        if (connectionId == null) {
            throw new GuacamoleResourceNotFoundException("No dynamic connection found");
        }
        
        // 获取配置并创建隧道
        GuacamoleConfiguration config = dynamicService.getConfiguration(connectionId);
        
        // 手动创建隧道
        GuacamoleSocket socket = new ConfiguredGuacamoleSocket(
            new InetGuacamoleSocket("localhost", 4822),
            config
        );
        
        return new SimpleGuacamoleTunnel(socket);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException {
        
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
                
                if (username != null) {
                    config.setParameter("username", username);
                }
                if (password != null) {
                    config.setParameter("password", password);
                }
                
                // 创建动态连接
                String connectionId = dynamicService.createDynamicConnection(config);
                
                // 存储到会话
                HttpSession session = request.getSession();
                session.setAttribute("dynamicConnectionId", connectionId);
                
                // 重定向到标准 Guacamole 客户端
                String redirectUrl = request.getContextPath() + "/#/client/" + connectionId;
                response.sendRedirect(redirectUrl);
                
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameters: protocol and hostname");
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