package io.github.minemon.world.config;

import io.github.minemon.world.service.impl.JsonWorldDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorldConfiguration {

    @Value("${world.seed:12345}")
    private long seed;

    @Bean(name = "clientJsonWorldDataService")
    public JsonWorldDataService clientJsonWorldDataService() {
        return new JsonWorldDataService("save/worlds", false);
    }
    @Bean
    public WorldConfig worldConfig() {
        return new WorldConfig(seed);
    }
}
