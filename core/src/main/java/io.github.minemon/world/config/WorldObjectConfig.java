package io.github.minemon.world.config;

import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.impl.ServerWorldObjectManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class WorldObjectConfig {

    @Bean
    @Profile("server")
    public WorldObjectManager serverWorldObjectManager() {
        return new ServerWorldObjectManagerImpl();
    }
}
