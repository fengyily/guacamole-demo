package org.apache.guacamole.dynamic;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DynamicConnectionAuthenticationProvider extends AbstractAuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(DynamicConnectionAuthenticationProvider.class);
    
    private final DynamicConnectionService dynamicService = new DynamicConnectionService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 加密配置
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String ENCRYPTION_KEY_ENV = "GUACAMOLE_ENCRYPTION_KEY"; // 环境变量名
    
    // 加密密钥（从环境变量获取）
    private String getEncryptionKey() {
        String key = System.getenv(ENCRYPTION_KEY_ENV);
        if (key == null || key.trim().isEmpty()) {
            logger.error("❌ Encryption key not found in environment variable: {}", ENCRYPTION_KEY_ENV);
            throw new RuntimeException("Encryption key not configured");
        }
        
        // 验证密钥长度（AES-256需要32字节）
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            logger.error("❌ Invalid encryption key length: {} bytes (expected: 32 bytes)", keyBytes.length);
            throw new RuntimeException("Encryption key must be 32 bytes for AES-256");
        }
        
        logger.info("✅ Encryption key loaded successfully ({} bytes)", keyBytes.length);
        return key;
    }

    @Override
    public String getIdentifier() {
        return "dynamic-connection";
    }

    @Override
    public AuthenticatedUser authenticateUser(Credentials credentials) throws GuacamoleException {
        logger.info("=== DynamicConnectionAuthenticationProvider.authenticateUser() CALLED ===");
        
        HttpServletRequest request = credentials.getRequest();
        if (request == null) {
            logger.info("Request is null");
            return null;
        }
        
        // 优先处理token参数
        String token = request.getParameter("token");
        if (token != null && !token.trim().isEmpty()) {
            logger.info("🔐 Token parameter found");
            return handleTokenAuthentication(token, credentials, request);
        }
        
        // 回退到原有参数方式
        return handleLegacyAuthentication(credentials, request);
    }

    /**
     * 处理token认证
     */
    private AuthenticatedUser handleTokenAuthentication(String token, Credentials credentials, HttpServletRequest request) {
        try {
            // 解密token
            String decryptedJson = decryptGCM(token);
            logger.info("✅ Token decrypted successfully", decryptedJson);
            
            // 解析JSON数据
            ConnectionData connData = objectMapper.readValue(decryptedJson, ConnectionData.class);
            logger.info("✅ JSON parsed: {}://{}:{}", connData.protocol, connData.hostname, connData.port);
            
            // 检查是否已经重定向过
            String redirected = request.getParameter("_redirected");
            if (!"true".equals(redirected)) {
                if (attemptCleanRedirectFromToken(credentials, connData, request)) {
                    return null;
                }
            }
            
            logger.info("✅✅✅ DYNAMIC CONNECTION AUTHENTICATED via TOKEN: {}://{}:{} ✅✅✅", 
                       connData.protocol, connData.hostname, connData.port);
            
            // 🔥 关键修改：传递连接数据到AuthenticatedUser
            return new DynamicAuthenticatedUser(credentials, this, connData);
            
        } catch (Exception e) {
            logger.error("❌ Token authentication failed: {}", e.getMessage(), e);
            return null;
        }
    }
private AuthenticatedUser handleLegacyAuthentication(Credentials credentials, HttpServletRequest request) {
    String protocol = request.getParameter("protocol");
    String hostname = request.getParameter("hostname");
    
    logger.info("Legacy parameters - protocol: '{}', hostname: '{}'", protocol, hostname);
    
    if (protocol != null && hostname != null) {
        logger.info("✅✅✅ DYNAMIC CONNECTION AUTHENTICATED: {}://{} ✅✅✅", protocol, hostname);
        
        // 🔥 关键修改：正确处理端口参数的类型转换
        ConnectionData connData = new ConnectionData();
        connData.protocol = protocol;
        connData.hostname = hostname;
        
        // 处理端口参数
        String portParam = request.getParameter("port");
        if (portParam != null && !portParam.trim().isEmpty()) {
            try {
                connData.port = Integer.parseInt(portParam);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port number: {}, using default port", portParam);
                connData.port = getDefaultPort(protocol);
            }
        } else {
            connData.port = getDefaultPort(protocol);
        }
        
        connData.username = request.getParameter("username");
        connData.password = request.getParameter("password");
        connData.timestamp = System.currentTimeMillis();
        
        String redirected = request.getParameter("_redirected");
        if (!"true".equals(redirected)) {
            if (attemptCleanRedirect(credentials, protocol, hostname, request)) {
                return null;
            }
        }
        
        // 🔥 关键修改：传递连接数据到AuthenticatedUser
        return new DynamicAuthenticatedUser(credentials, this, connData);
    }
    
    logger.info("Not a dynamic connection request");
    return null;
}

    /**
     * AES-GCM解密
     */
    private String decryptGCM(String ciphertext) throws Exception {
        try {
            // 从环境变量获取密钥
            String encryptionKey = getEncryptionKey();
            
            // Base64解码
            byte[] decoded = Base64.getUrlDecoder().decode(ciphertext);
            
            // 创建AES密钥
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            // GCM模式需要提取nonce（通常是前12字节）
            int nonceSize = 12; // GCM通常使用12字节nonce
            if (decoded.length < nonceSize) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            
            byte[] nonce = new byte[nonceSize];
            byte[] ciphertextBytes = new byte[decoded.length - nonceSize];
            System.arraycopy(decoded, 0, nonce, 0, nonceSize);
            System.arraycopy(decoded, nonceSize, ciphertextBytes, 0, ciphertextBytes.length);
            
            // 配置GCM参数
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            
            // 解密
            byte[] decrypted = cipher.doFinal(ciphertextBytes);
            return new String(decrypted, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("Decryption failed for token: {}", ciphertext);
            throw new RuntimeException("Token decryption failed", e);
        }
    }

    /**
     * 基于token数据的重定向
     */
    private boolean attemptCleanRedirectFromToken(Credentials credentials, ConnectionData connData, HttpServletRequest request) {
        try {
            GuacamoleConfiguration config = new GuacamoleConfiguration();
            config.setProtocol(connData.protocol);
            config.setParameter("hostname", connData.hostname);
            config.setParameter("port", String.valueOf(connData.port));
            
            if (connData.username != null) 
                config.setParameter("username", connData.username);
            if (connData.password != null) 
                config.setParameter("password", connData.password);

            String connectionId = dynamicService.createDynamicConnection(config);
            String contextPath = request.getContextPath();
            String cleanUrl = contextPath + "/#/client/" + connectionId + "?_redirected=true";
            
            logger.info("🔗 Redirecting to clean URL: {}", cleanUrl);
            
            HttpServletResponse response = getHttpResponse(credentials);
            if (response != null) {
                response.sendRedirect(cleanUrl);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Redirect failed", e);
        }
        return false;
    }

    /**
     * 原有的重定向逻辑
     */
    private boolean attemptCleanRedirect(Credentials credentials, String protocol, String hostname, HttpServletRequest request) {
        try {
            GuacamoleConfiguration config = new GuacamoleConfiguration();
            config.setProtocol(protocol);
            config.setParameter("hostname", hostname);
            config.setParameter("port", request.getParameter("port") != null ? request.getParameter("port") : getDefaultPort(protocol) + "");
            
            if (request.getParameter("username") != null) 
                config.setParameter("username", request.getParameter("username"));
            if (request.getParameter("password") != null) 
                config.setParameter("password", request.getParameter("password"));

            String connectionId = dynamicService.createDynamicConnection(config);
            String contextPath = request.getContextPath();
            String cleanUrl = contextPath + "/#/client/" + connectionId + "?_redirected=true";
            
            logger.info("🔗 Redirecting to clean URL: {}", cleanUrl);
            
            HttpServletResponse response = getHttpResponse(credentials);
            if (response != null) {
                response.sendRedirect(cleanUrl);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Redirect failed", e);
        }
        return false;
    }

    // 连接数据类
    public static class ConnectionData {
        public String protocol;
        public String hostname;
        public int port;
        public String username;
        public String password;
        public long timestamp;
        
        // getters/setters
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    private HttpServletResponse getHttpResponse(Credentials credentials) {
        try {
            Method getResponseMethod = credentials.getClass().getMethod("getResponse");
            Object response = getResponseMethod.invoke(credentials);
            return (HttpServletResponse) response;
        } catch (Exception e) {
            logger.debug("Could not get HttpServletResponse from credentials");
            return null;
        }
    }

    private int getDefaultPort(String protocol) {
        switch (protocol.toLowerCase()) {
            case "ssh": return 22;
            case "rdp": return 3389;
            case "vnc": return 5900;
            default: return 22;
        }
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser) throws GuacamoleException {
        logger.info("🎯 getUserContext() called");
        
        // 🔥 关键修改：检查是否有连接数据
        if (authenticatedUser instanceof DynamicAuthenticatedUser) {
            DynamicAuthenticatedUser dynamicUser = (DynamicAuthenticatedUser) authenticatedUser;
            ConnectionData connData = dynamicUser.getConnectionData();
            
            if (connData != null) {
                logger.info("✅ Using connection data from authenticated user: {}://{}", 
                           connData.protocol, connData.hostname);
                return new DynamicUserContext(authenticatedUser, dynamicService, connData);
            }
        }
        
        // 如果没有连接数据，回退到原有方式
        logger.info("ℹ️ No connection data found, using legacy parameter method");
        return new DynamicUserContext(authenticatedUser, dynamicService);
    }
}