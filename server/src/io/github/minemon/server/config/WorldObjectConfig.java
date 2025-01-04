package io.github.minemon.server.config;

import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.server.world.ServerWorldObjectManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorldObjectConfig {

    @Bean
    public WorldObjectManager serverWorldObjectManager() {
        return new ServerWorldObjectManagerImpl();
    }
}
