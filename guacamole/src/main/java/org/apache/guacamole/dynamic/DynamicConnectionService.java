package org.apache.guacamole.dynamic;

import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicConnectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicConnectionService.class);
    private final Map<String, GuacamoleConfiguration> connectionStore = new ConcurrentHashMap<>();
    
    public String createDynamicConnection(GuacamoleConfiguration config) throws GuacamoleException {
        try {
            String connectionId = "dynamic-" + UUID.randomUUID().toString();
            connectionStore.put(connectionId, config);
            logger.debug("Created dynamic connection: {}", connectionId);
            return connectionId;
        } catch (Exception e) {
            throw new GuacamoleException("Failed to create dynamic connection", e);
        }
    }
    
    public GuacamoleConfiguration getConfiguration(String connectionId) throws GuacamoleException {
        GuacamoleConfiguration config = connectionStore.get(connectionId);
        if (config == null) {
            throw new GuacamoleException("Dynamic connection not found: " + connectionId);
        }
        return config;
    }
}