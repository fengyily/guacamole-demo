package org.apache.guacamole.dynamic;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.*;
import org.apache.guacamole.net.auth.simple.SimpleConnection;
import org.apache.guacamole.net.auth.simple.SimpleConnectionDirectory;
import org.apache.guacamole.net.auth.simple.SimpleConnectionGroup;
import org.apache.guacamole.net.auth.simple.SimpleConnectionGroupDirectory;
import org.apache.guacamole.net.auth.simple.SimpleUser;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class DynamicUserContext extends AbstractUserContext {

    private final AuthenticatedUser authenticatedUser;
    private final DynamicConnectionService dynamicService;
    private final Set<Connection> connections = new HashSet<>();
    private final Set<ConnectionGroup> connectionGroups = new HashSet<>();
    private String redirectConnectionId = null;

    public DynamicUserContext(AuthenticatedUser authenticatedUser, DynamicConnectionService dynamicService) 
            throws GuacamoleException {
        this.authenticatedUser = authenticatedUser;
        this.dynamicService = dynamicService;
        createDynamicConnection();
        createRootConnectionGroup();
    }

    @Override
    public User self() {
        return new SimpleUser("dynamic-user") {
            @Override
            public String getIdentifier() { 
                return "dynamic-user"; 
            }
        };
    }

    @Override
    public AuthenticationProvider getAuthenticationProvider() {
        return authenticatedUser.getAuthenticationProvider();
    }

    @Override
    public org.apache.guacamole.net.auth.Directory<Connection> getConnectionDirectory() throws GuacamoleException {
        System.out.println("🎯 getConnectionDirectory() called, connections count: " + connections.size());
        for (Connection conn : connections) {
            System.out.println("   Connection: " + conn.getName() + " (ID: " + conn.getIdentifier() + ")");
        }
        return new SimpleConnectionDirectory(connections);
    }

    @Override
    public org.apache.guacamole.net.auth.Directory<ConnectionGroup> getConnectionGroupDirectory() throws GuacamoleException {
        System.out.println("🎯 getConnectionGroupDirectory() called, groups count: " + connectionGroups.size());
        return new SimpleConnectionGroupDirectory(connectionGroups);
    }

    private void createRootConnectionGroup() {
        SimpleConnectionGroup rootGroup = new SimpleConnectionGroup(
            "ROOT", 
            "ROOT", 
            Collections.emptyList(),
            Collections.emptyList()
        );
        connectionGroups.add(rootGroup);
        System.out.println("✅ Created ROOT connection group");
    }

    private void createDynamicConnection() throws GuacamoleException {
        HttpServletRequest request = authenticatedUser.getCredentials().getRequest();
        String protocol = request.getParameter("protocol");
        String hostname = request.getParameter("hostname");
        String port = request.getParameter("port");
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if (protocol == null || hostname == null) {
            System.out.println("❌ Missing protocol or hostname parameters");
            return;
        }

        System.out.println("🎯 Creating dynamic connection:");
        System.out.println("   Protocol: " + protocol);
        System.out.println("   Hostname: " + hostname);
        System.out.println("   Port: " + (port != null ? port : getDefaultPort(protocol)));
        System.out.println("   Username: " + username);

        // 创建配置
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol(protocol);
        config.setParameter("hostname", hostname);
        config.setParameter("port", port != null ? port : getDefaultPort(protocol));
        
        if (username != null) config.setParameter("username", username);
        if (password != null) config.setParameter("password", password);

        // 设置协议特定参数
        configureProtocolSpecificParameters(config, protocol);

        // 使用 DynamicConnectionService 创建连接 ID 和存储配置
        String connectionId = dynamicService.createDynamicConnection(config);
        this.redirectConnectionId = connectionId; // 保存用于重定向
        
        // 创建连接对象
        SimpleConnection connection = new SimpleConnection(connectionId, "ROOT", config, true);
        connection.setName("Dynamic - " + protocol.toUpperCase() + " to " + hostname);
        connection.setParentIdentifier("ROOT");
        
        connections.add(connection);
        
        System.out.println("✅✅✅ SUCCESS: Created dynamic connection:");
        System.out.println("   Name: " + connection.getName());
        System.out.println("   ID: " + connection.getIdentifier());
        System.out.println("   Parent: " + connection.getParentIdentifier());
        System.out.println("   Protocol: " + protocol);
    }

    /**
     * 获取重定向的连接ID（用于清理URL）
     */
    public String getRedirectConnectionId() {
        return redirectConnectionId;
    }

    /**
     * 执行重定向到干净的URL
     */
    public void redirectToCleanUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (redirectConnectionId != null) {
            String cleanUrl = request.getContextPath() + "/#/client/" + redirectConnectionId;
            System.out.println("🔗 Redirecting to clean URL: " + cleanUrl);
            response.sendRedirect(cleanUrl);
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

    private void configureProtocolSpecificParameters(GuacamoleConfiguration config, String protocol) {
        switch (protocol.toLowerCase()) {
            case "rdp":
                config.setParameter("security", "any");
                config.setParameter("ignore-cert", "true");
                config.setParameter("dpi", "96");
                break;
            case "ssh":
                config.setParameter("font-name", "Menlo, Consolas, monospace");
                config.setParameter("font-size", "12");
                break;
            case "vnc":
                config.setParameter("color-depth", "32");
                break;
        }
    }
}