package io.github.minemon.player.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "player")
public class PlayerProperties {

    
    private String username = "Player";

    
    private float walkStepDuration = 0.3f;

    
    private float runStepDuration = 0.15f;



}
