package io.github.minemon.server.config;

import io.github.minemon.core.service.FileAccessService;
import io.github.minemon.core.service.impl.LocalFileAccessService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.server.service.MultiplayerServer;
import io.github.minemon.server.service.MultiplayerService;
import io.github.minemon.server.service.impl.MultiplayerServerImpl;
import io.github.minemon.server.service.impl.MultiplayerServiceImpl;
import io.github.minemon.world.service.WorldService;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.multiplayer.service.impl.ServerConnectionServiceImpl;
import io.github.minemon.event.EventBus;
import io.github.minemon.server.AuthService;
import io.github.minemon.world.service.impl.JsonWorldDataService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class MultiplayerConfig {

    @Bean
    public FileAccessService fileAccessService() {
        return new LocalFileAccessService();
    }

    @Bean
    public ServerConnectionService serverConnectionService(FileAccessService fileAccessService) {
        return new ServerConnectionServiceImpl(fileAccessService);
    }

    @Bean
    public MultiplayerService multiplayerService(WorldService worldService, PlayerService playerService, Environment env) {
        return new MultiplayerServiceImpl(worldService, env);
    }

    @Bean(name = "serverJsonWorldDataService")
    public JsonWorldDataService serverJsonWorldDataService() {
        return new JsonWorldDataService("save/worlds", true);
    }
    @Bean
    public MultiplayerServer multiplayerServer(MultiplayerService multiplayerService, EventBus eventBus, AuthService authService) {
        return new MultiplayerServerImpl(multiplayerService, eventBus, authService);
    }

}
