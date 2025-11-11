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
    private String dynamicConnectionId = null;

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

    // 🔥 关键修复：使用正确的 ConnectionDirectory 实现 🔥
    @Override
    public org.apache.guacamole.net.auth.Directory<Connection> getConnectionDirectory() throws GuacamoleException {
        // 在返回目录前确保连接存在
        refreshConnections();
        
        System.out.println("🎯 getConnectionDirectory() called, connections count: " + connections.size());
        for (Connection conn : connections) {
            System.out.println("   Connection: " + conn.getName() + " (ID: " + conn.getIdentifier() + ")");
        }
        
        // 创建自定义的 ConnectionDirectory 来处理连接检索
        return new CustomConnectionDirectory(connections, dynamicService);
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
            throw new GuacamoleException("Missing required parameters: protocol and hostname");
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
        this.dynamicConnectionId = connectionId;
        
        // 刷新连接集合
        refreshConnections();
        
        System.out.println("✅✅✅ SUCCESS: Created dynamic connection:");
        System.out.println("   Name: Dynamic - " + protocol.toUpperCase() + " to " + hostname);
        System.out.println("   ID: " + connectionId);
        System.out.println("   Parent: ROOT");
        System.out.println("   Protocol: " + protocol);
    }

    /**
     * 刷新连接集合
     */
    private void refreshConnections() {
        connections.clear();
        
        if (dynamicConnectionId != null) {
            try {
                GuacamoleConfiguration config = dynamicService.getConfiguration(dynamicConnectionId);
                if (config != null) {
                    SimpleConnection connection = new SimpleConnection(dynamicConnectionId, "ROOT", config, true);
                    connection.setName("Dynamic - " + config.getProtocol().toUpperCase() + " to " + config.getParameter("hostname"));
                    connection.setParentIdentifier("ROOT");
                    connections.add(connection);
                    System.out.println("🔄 Refreshed connection: " + connection.getName());
                }
            } catch (GuacamoleException e) {
                System.out.println("❌ Error refreshing connection: " + e.getMessage());
            }
        }
    }

    /**
     * 获取动态连接ID
     */
    public String getDynamicConnectionId() {
        return dynamicConnectionId;
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

    // 🔥 自定义 ConnectionDirectory 来处理连接检索 🔥
    private class CustomConnectionDirectory extends SimpleConnectionDirectory {
        
        private final DynamicConnectionService dynamicService;
        
        public CustomConnectionDirectory(Collection<Connection> connections, DynamicConnectionService dynamicService) {
            super(connections);
            this.dynamicService = dynamicService;
        }
        
        @Override
        public Connection get(String identifier) throws GuacamoleException {
            System.out.println("🔍 CustomConnectionDirectory.get() called for ID: " + identifier);
            
            // 首先尝试从父类获取
            Connection connection = super.get(identifier);
            if (connection != null) {
                System.out.println("✅ Found connection in directory: " + connection.getName());
                return connection;
            }
            
            // 如果父类没有找到，尝试从 dynamicService 获取
            try {
                GuacamoleConfiguration config = dynamicService.getConfiguration(identifier);
                if (config != null) {
                    SimpleConnection dynamicConnection = new SimpleConnection(identifier, "ROOT", config, true);
                    dynamicConnection.setName("Dynamic - " + config.getProtocol().toUpperCase() + " to " + config.getParameter("hostname"));
                    dynamicConnection.setParentIdentifier("ROOT");
                    System.out.println("✅✅✅ SUCCESS: Retrieved dynamic connection: " + dynamicConnection.getName());
                    return dynamicConnection;
                }
            } catch (GuacamoleException e) {
                System.out.println("❌ Connection not found in dynamic service: " + identifier);
            }
            
            System.out.println("❌ Connection not found: " + identifier);
            return null;
        }
    }
}