package org.apache.guacamole.dynamic;

import com.google.inject.AbstractModule;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicConnectionModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(DynamicConnectionModule.class);

    @Override
    protected void configure() {
        logger.info("🎯🎯🎯 DYNAMIC CONNECTION MODULE IS BEING CONFIGURED! 🎯🎯🎯");
        logger.info("Binding DynamicConnectionAuthenticationProvider");
        
        bind(AuthenticationProvider.class).to(DynamicConnectionAuthenticationProvider.class);
        
        logger.info("✅✅✅ DYNAMIC CONNECTION MODULE CONFIGURATION COMPLETE ✅✅✅");
    }
}