package org.apache.guacamole.dynamic;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.auth.AbstractAuthenticationProvider;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

import org.apache.guacamole.net.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class DynamicConnectionAuthenticationProvider extends AbstractAuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(DynamicConnectionAuthenticationProvider.class);
    
    // 手动创建服务实例
    private final DynamicConnectionService dynamicService = new DynamicConnectionService();

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
        
        String protocol = request.getParameter("protocol");
        String hostname = request.getParameter("hostname");
        
        logger.info("Parameters - protocol: '{}', hostname: '{}'", protocol, hostname);
        
        if (protocol != null && hostname != null) {
            logger.info("✅✅✅ DYNAMIC CONNECTION AUTHENTICATED: {}://{} ✅✅✅", protocol, hostname);
            
            // 检查是否已经重定向过（避免循环）
            String redirected = request.getParameter("_redirected");
            if (!"true".equals(redirected)) {
                // 尝试重定向到干净URL
                if (attemptRedirect(credentials, protocol, hostname, request)) {
                    return null; // 返回null表示认证被重定向中断
                }
            }
            
            return new DynamicAuthenticatedUser(credentials, this);
        }
        
        logger.info("Not a dynamic connection request");
        return null;
    }

    /**
     * 尝试重定向到干净URL
     */
    private boolean attemptRedirect(Credentials credentials, String protocol, String hostname, HttpServletRequest request) {
        try {
            // 创建动态连接配置
            GuacamoleConfiguration config = new GuacamoleConfiguration();
            config.setProtocol(protocol);
            config.setParameter("hostname", hostname);
            config.setParameter("port", request.getParameter("port") != null ? request.getParameter("port") : getDefaultPort(protocol));
            
            if (request.getParameter("username") != null) 
                config.setParameter("username", request.getParameter("username"));
            if (request.getParameter("password") != null) 
                config.setParameter("password", request.getParameter("password"));

            // 创建连接ID
            String connectionId = dynamicService.createDynamicConnection(config);
            
            // 构建干净URL
            String contextPath = request.getContextPath();
            String cleanUrl = contextPath + "/#/?_redirected=true&protocol=" + protocol + "&hostname=" + hostname;
            
            // 添加其他参数
            if (request.getParameter("port") != null) 
                cleanUrl += "&port=" + request.getParameter("port");
            if (request.getParameter("username") != null) 
                cleanUrl += "&username=" + request.getParameter("username");
            
            logger.info("🔗 Attempting redirect to: {}", cleanUrl);
            
            // 通过反射获取HttpServletResponse并重定向
            HttpServletResponse response = getHttpResponse(credentials);
            if (response != null) {
                response.sendRedirect(cleanUrl);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Redirect failed, continuing with normal authentication", e);
        }
        
        return false;
    }

    /**
     * 通过反射获取HttpServletResponse
     */
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

    private String getDefaultPort(String protocol) {
        switch (protocol.toLowerCase()) {
            case "ssh": return "22";
            case "rdp": return "3389";
            case "vnc": return "5900";
            default: return "22";
        }
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser) throws GuacamoleException {
        logger.info("🎯 getUserContext() called");
        return new DynamicUserContext(authenticatedUser, dynamicService);
    }
}