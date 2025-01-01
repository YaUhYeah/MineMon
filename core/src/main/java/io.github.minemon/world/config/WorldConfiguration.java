package io.github.minemon.world.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorldConfiguration {

    @Value("${world.seed:12345}")
    private long seed;

    @Bean
    public WorldConfig worldConfig() {
        return new WorldConfig(seed);
    }
}
