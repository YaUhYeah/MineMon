package io.github.minemon.config;

import io.github.minemon.world.service.impl.AndroidJsonWorldDataService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("android")
public class AndroidConfig {
    @Bean
    public AndroidJsonWorldDataService androidJsonWorldDataService() {
        return new AndroidJsonWorldDataService();
    }
}