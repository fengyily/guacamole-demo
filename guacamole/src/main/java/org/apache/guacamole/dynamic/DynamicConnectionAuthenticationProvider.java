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
            return new DynamicAuthenticatedUser(credentials, this);
        }
        
        logger.info("Not a dynamic connection request");
        return null;
    }

    @Override
    public UserContext getUserContext(AuthenticatedUser authenticatedUser) throws GuacamoleException {
        logger.info("🎯 getUserContext() called");
        return new DynamicUserContext(authenticatedUser, dynamicService);
    }
}