package io.github.minemon.core.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@Primary
@ConfigurationProperties(prefix = "game")
public class GameConfig {
    private List<String> motds;

    @PostConstruct
    public void init() {
        if (motds == null) {
            motds = new ArrayList<>();
            motds.add("Welcome to MineMon - Catch them all in an open world!");
            motds.add("Explore, battle, and collect in a vast world!");
            motds.add("New updates coming soon!");
            motds.add("Can you catch them all?");
            motds.add("Adventure awaits in the world of MineMon!");
            motds.add("Become the ultimate trainer!");
            motds.add("Build, battle, explore!");
        }
    }

    public String getRandomMotd() {
        if (motds == null || motds.isEmpty()) {
            init();
        }
        return motds.get((int) (Math.random() * motds.size()));
    }
}