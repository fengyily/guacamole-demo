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
import java.util.*;

public class DynamicUserContext extends AbstractUserContext {

    private final AuthenticatedUser authenticatedUser;
    private final DynamicConnectionService dynamicService;
    private final Map<String, Connection> connections = new HashMap<>();
    private final Set<ConnectionGroup> connectionGroups = new HashSet<>();
    private String dynamicConnectionId = null;

    // 原有构造函数（向后兼容）
    public DynamicUserContext(AuthenticatedUser authenticatedUser, DynamicConnectionService dynamicService) 
            throws GuacamoleException {
        this.authenticatedUser = authenticatedUser;
        this.dynamicService = dynamicService;
        createDynamicConnection();
        createRootConnectionGroup();
    }

    // 🔥 新增构造函数：接受连接数据
    public DynamicUserContext(AuthenticatedUser authenticatedUser, DynamicConnectionService dynamicService,
                            DynamicConnectionAuthenticationProvider.ConnectionData connData) 
            throws GuacamoleException {
        this.authenticatedUser = authenticatedUser;
        this.dynamicService = dynamicService;
        createDynamicConnectionFromData(connData);
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
        for (Connection conn : connections.values()) {
            System.out.println("   Connection: " + conn.getName() + " (ID: " + conn.getIdentifier() + ")");
        }
        return new DynamicConnectionDirectory(connections, dynamicService);
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

    // 🔥 新增方法：从连接数据创建动态连接
    private void createDynamicConnectionFromData(DynamicConnectionAuthenticationProvider.ConnectionData connData) 
            throws GuacamoleException {
        
        if (connData.protocol == null || connData.hostname == null) {
            throw new GuacamoleException("Missing required parameters: protocol and hostname");
        }

        System.out.println("🎯 Creating dynamic connection from connection data:");
        System.out.println("   Protocol: " + connData.protocol);
        System.out.println("   Hostname: " + connData.hostname);
        System.out.println("   Port: " + connData.port);
        System.out.println("   Username: " + connData.username);

        // 创建配置
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol(connData.protocol);
        config.setParameter("hostname", connData.hostname);
        config.setParameter("port", String.valueOf(connData.port));
        
        if (connData.username != null) config.setParameter("username", connData.username);
        if (connData.password != null) config.setParameter("password", connData.password);

        // 设置协议特定参数
        configureProtocolSpecificParameters(config, connData.protocol);

        // 使用 DynamicConnectionService 创建连接 ID 和存储配置
        String connectionId = dynamicService.createDynamicConnection(config);
        this.dynamicConnectionId = connectionId;
        
        // 创建连接对象
        SimpleConnection connection = new SimpleConnection(connectionId, connectionId, config, true);
        connection.setName("Dynamic - " + connData.protocol.toUpperCase() + " to " + connData.hostname);
        connection.setParentIdentifier("ROOT");
        
        connections.put(connectionId, connection);
        
        System.out.println("✅✅✅ SUCCESS: Created dynamic connection from data:");
        System.out.println("   Name: " + connection.getName());
        System.out.println("   ID: " + connection.getIdentifier());
        System.out.println("   Parent: " + connection.getParentIdentifier());
        System.out.println("   Protocol: " + connData.protocol);
        
        // 验证连接是否正确存储
        Connection storedConn = connections.get(connectionId);
        if (storedConn != null) {
            System.out.println("✅ Verification: Connection successfully stored with ID: " + storedConn.getIdentifier());
        } else {
            System.out.println("❌ Verification FAILED: Connection not found in map!");
        }
    }

    // 原有方法：从HTTP参数创建动态连接
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

        System.out.println("🎯 Creating dynamic connection from HTTP parameters:");
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
        
        // 🔥🔥🔥 关键修复：确保使用正确的连接ID创建连接对象 🔥🔥🔥
        SimpleConnection connection = new SimpleConnection(connectionId, connectionId, config, true);
        connection.setName("Dynamic - " + protocol.toUpperCase() + " to " + hostname);
        connection.setParentIdentifier("ROOT");
        
        connections.put(connectionId, connection);
        
        System.out.println("✅✅✅ SUCCESS: Created dynamic connection:");
        System.out.println("   Name: " + connection.getName());
        System.out.println("   ID: " + connection.getIdentifier()); // 这里应该是动态ID
        System.out.println("   Parent: " + connection.getParentIdentifier());
        System.out.println("   Protocol: " + protocol);
        System.out.println("   Stored in connections map with key: " + connectionId);
        
        // 验证连接是否正确存储
        Connection storedConn = connections.get(connectionId);
        if (storedConn != null) {
            System.out.println("✅ Verification: Connection successfully stored with ID: " + storedConn.getIdentifier());
        } else {
            System.out.println("❌ Verification FAILED: Connection not found in map!");
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

    private static class DynamicConnectionDirectory extends SimpleConnectionDirectory {
    
        private final Map<String, Connection> connectionMap;
        private final DynamicConnectionService dynamicService;
        
        public DynamicConnectionDirectory(Map<String, Connection> connectionMap, DynamicConnectionService dynamicService) {
            super(connectionMap.values());
            this.connectionMap = connectionMap;
            this.dynamicService = dynamicService;
        }
        
        @Override
        public Connection get(String identifier) throws GuacamoleException {
            System.out.println("🔍 DynamicConnectionDirectory.get() called for ID: " + identifier);
            System.out.println("   Available connections in map: " + connectionMap.keySet());
            
            // 🚨 关键修复：首先检查连接是否存在
            if (!dynamicService.connectionExists(identifier)) {
                System.out.println("❌ Connection does not exist in service: " + identifier);
                return null;
            }
            
            // 首先从连接映射中查找
            Connection connection = connectionMap.get(identifier);
            if (connection != null) {
                System.out.println("✅ Found connection in map: " + connection.getName() + " (ID: " + identifier + ")");
                return connection;
            }
            
            // 如果没找到，从动态服务创建
            try {
                System.out.println("🔄 Creating connection from dynamic service: " + identifier);
                GuacamoleConfiguration config = dynamicService.getConfiguration(identifier);
                if (config != null) {
                    SimpleConnection dynamicConnection = new SimpleConnection(identifier, identifier, config, true);
                    dynamicConnection.setName("Dynamic - " + config.getProtocol().toUpperCase() + " to " + config.getParameter("hostname"));
                    dynamicConnection.setParentIdentifier("ROOT");
                    
                    // 🚨 关键：添加到映射中以便后续使用
                    connectionMap.put(identifier, dynamicConnection);
                    
                    System.out.println("✅✅✅ SUCCESS: Created and returned dynamic connection: " + dynamicConnection.getName());
                    return dynamicConnection;
                }
            } catch (GuacamoleException e) {
                System.out.println("❌ Failed to create dynamic connection " + identifier + ": " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("❌ Connection not found: " + identifier);
            return null;
        }
    }
}