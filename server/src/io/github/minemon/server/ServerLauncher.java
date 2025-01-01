package io.github.minemon.server;

import io.github.minemon.lwjgl3.PokemeetupApplication;
import io.github.minemon.multiplayer.model.ServerConnectionConfig;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.plugin.PluginManager;
import io.github.minemon.server.service.MultiplayerServer;
import io.github.minemon.world.service.WorldService;  // <-- Assuming your world service is here
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class ServerLauncher {

    public static void main(String[] args) {

        int tcpPort = 54555;
        int udpPort = 54777;

        if (args.length > 0) {
            try {
                tcpPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.warn("Invalid TCP port argument, using default: {}", tcpPort);
            }
        }

        if (args.length > 1) {
            try {
                udpPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                log.warn("Invalid UDP port argument, using default: {}", udpPort);
            }
        }

        Path baseDir = Paths.get("").toAbsolutePath();
        log.info("Base directory: {}", baseDir);

        try {
            DeploymentHelper.createServerDeployment(baseDir);
        } catch (Exception e) {
            log.error("Failed to create server deployment: {}", e.getMessage());
            System.exit(1);
        }

        SpringApplication app = new SpringApplication(PokemeetupApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAdditionalProfiles("server");
        ConfigurableApplicationContext context = app.run(args);

        // NEW CALL: ensure the server's default world is created or loaded
        onServerStart(context);

        MultiplayerServer server = context.getBean(MultiplayerServer.class);
        ServerConnectionService connectionService = context.getBean(ServerConnectionService.class);
        PluginManager pluginManager = context.getBean(PluginManager.class);

        ServerConnectionConfig config = connectionService.loadConfig();
        if (config.getServerIP() == null) {
            config.setServerName("PokeMeetupServer");
            config.setServerIP("0.0.0.0");
            config.setTcpPort(tcpPort);
            config.setUdpPort(udpPort);
            config.setMotd("Welcome to PokeMeetup!");
            config.setMaxPlayers(20);
            connectionService.saveConfig(config);
        }

        server.startServer(config.getTcpPort(), config.getUdpPort());
        log.info("Server started on TCP: {}, UDP: {}", config.getTcpPort(), config.getUdpPort());

        Path pluginsDir = baseDir.resolve("plugins");
        pluginManager.loadPlugins(pluginsDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down server...");
            pluginManager.unloadAll();
            server.stopServer();
        }));

        final int TICKS_PER_SECOND = 20;
        final long OPTIMAL_TIME = 1_000_000_000 / TICKS_PER_SECOND;
        long lastLoopTime = System.nanoTime();

        while (context.isActive()) {
            long now = System.nanoTime();
            long updateLength = now - lastLoopTime;
            lastLoopTime = now;

            float deltaSeconds = updateLength / 1_000_000_000f;
            server.processMessages(deltaSeconds);

            long sleepTime = (OPTIMAL_TIME - (System.nanoTime() - lastLoopTime)) / 1_000_000;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Main loop interrupted: {}", e.getMessage());
                }
            }
        }

        pluginManager.unloadAll();
        server.stopServer();
        log.info("Server Stopped.");
    }

    /**
     * Called once the Spring context is up, before the main game loop starts.
     * Ensures we have a default server world created or loaded, so paths are valid.
     */
    private static void onServerStart(ConfigurableApplicationContext context) {
        // Example: we fetch the service needed to manage the server world:
        WorldService worldService = context.getBean(WorldService.class);

        // The default server world name
        String defaultServerWorldName = "serverWorld";

        // If the world doesn't exist, create it
        if (!worldService.getAvailableWorlds().contains(defaultServerWorldName)) {
            log.info("World '{}' does not exist. Creating it now...", defaultServerWorldName);
            boolean created = worldService.createWorld(defaultServerWorldName, 12345L);
            if (created) {
                log.info("Successfully created new default server world '{}'.", defaultServerWorldName);
            } else {
                log.warn("Failed to create world '{}'. Possibly it exists or there's an error.", defaultServerWorldName);
            }
        } else {
            // Otherwise, load it
            log.info("Loading existing server world '{}'.", defaultServerWorldName);
            worldService.loadWorld(defaultServerWorldName);
        }

        // Possibly call initIfNeeded (which might do chunk loading, etc.)
        worldService.initIfNeeded();
        log.info("Server world initialization complete.");
    }
}
