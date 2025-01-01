package io.github.minemon.core.config;

import com.badlogic.gdx.Game;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.core.screen.*;
import io.github.minemon.core.service.*;
import io.github.minemon.core.service.impl.ScreenManagerImpl;
import io.github.minemon.input.InputService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.model.WorldRenderer;
import io.github.minemon.world.service.ChunkLoaderService;
import io.github.minemon.world.service.ChunkPreloaderService;
import io.github.minemon.world.service.WorldService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScreenConfig {

    @Bean
    public ScreenManager screenManager(ApplicationContext applicationContext, Game game) {
        return new ScreenManagerImpl(applicationContext, game);
    }



}
