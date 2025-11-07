package org.apache.guacamole.dynamic;

import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.apache.guacamole.net.auth.Credentials;

import java.util.Collections;
import java.util.Set;

public class DynamicAuthenticatedUser implements AuthenticatedUser {

    private final Credentials credentials;
    private final AuthenticationProvider authProvider;
    private String identifier = "dynamic-user";

    public DynamicAuthenticatedUser(Credentials credentials, AuthenticationProvider authProvider) {
        this.credentials = credentials;
        this.authProvider = authProvider;
    }

    @Override 
    public String getIdentifier() { 
        return identifier; 
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override 
    public void invalidate() { 
        // 清理资源
    }

    @Override 
    public Credentials getCredentials() { 
        return credentials; 
    }

    @Override 
    public AuthenticationProvider getAuthenticationProvider() { 
        return authProvider; 
    }
    
    @Override
    public Set<String> getEffectiveUserGroups() {
        return Collections.emptySet();
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof DynamicAuthenticatedUser;
    }
    
    @Override
    public int hashCode() {
        return getIdentifier().hashCode();
    }
}