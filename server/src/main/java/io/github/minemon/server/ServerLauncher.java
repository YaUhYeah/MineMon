package io.github.minemon.server;

import io.github.minemon.lwjgl3.PokemeetupApplication;
import io.github.minemon.multiplayer.model.ServerConnectionConfig;
import io.github.minemon.multiplayer.service.MultiplayerServer;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(ServerLauncher.class);

    public static void main(String[] args) {

        int tcpPort = 54555;
        int udpPort = 54777;

        if (args.length > 0) {
            try {
                tcpPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid TCP port argument, using default: {}", tcpPort);
            }
        }

        if (args.length > 1) {
            try {
                udpPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid UDP port argument, using default: {}", udpPort);
            }
        }


        Path baseDir = Paths.get("").toAbsolutePath();
        logger.info("Base directory: {}", baseDir);

        try {
            DeploymentHelper.createServerDeployment(baseDir);
        } catch (Exception e) {
            logger.error("Failed to create server deployment: {}", e.getMessage());
            System.exit(1);
        }

        SpringApplication app = new SpringApplication(PokemeetupApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAdditionalProfiles("server");
        ConfigurableApplicationContext context = app.run(args);



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
        logger.info("Server started on TCP: {}, UDP: {}", config.getTcpPort(), config.getUdpPort());

        Path pluginsDir = baseDir.resolve("plugins");
        pluginManager.loadPlugins(pluginsDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");

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
                    logger.warn("Main loop interrupted: {}", e.getMessage());
                }
            }
        }

        pluginManager.unloadAll();
        server.stopServer();
        logger.info("Server Stopped.");
    }
}
