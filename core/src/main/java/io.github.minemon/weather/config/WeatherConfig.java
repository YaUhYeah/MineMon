package io.github.minemon.weather.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class WeatherConfig {
    public float getWeatherCheckInterval() {
        return 10f;
    }

    public int getMaxParticles() {
        return 300;
    }

    public float getMaxParticleSpawnRate() {
        return 300f;
    }

}
