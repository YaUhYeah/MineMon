package io.github.minemon.player.config;

import io.github.minemon.input.InputService;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.player.service.impl.PlayerServiceImpl;
import io.github.minemon.world.service.WorldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;


@Configuration
@EnableConfigurationProperties(PlayerProperties.class)
public class PlayerConfig {

    private final InputService inputService;
    private final PlayerProperties playerProperties;

    @Autowired
    @Lazy
    private WorldService worldService;

    @Autowired
    public PlayerConfig(InputService inputService, PlayerProperties playerProperties, WorldService worldService) {
        this.inputService = inputService;
        this.playerProperties = playerProperties;
        this.worldService = worldService;

    }


    @Bean
    public PlayerService playerService(
            PlayerAnimationService animationService, InventoryService inventoryService
    ) {
        return new PlayerServiceImpl(animationService, inputService, playerProperties, worldService,inventoryService);
    }
}
