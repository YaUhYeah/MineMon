package io.github.minemon.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

public class DeploymentHelper {
    private static final Logger logger = Logger.getLogger(DeploymentHelper.class.getName());


    public static void createServerDeployment(Path baseDir) throws IOException {

        Path runScript = baseDir.resolve("run.sh");
        if (Files.exists(runScript)) {
            logger.info("Deployment structure already set up at " + baseDir.toAbsolutePath());
            return;
        }


        createDirIfNotExists(baseDir, "config");
        createDirIfNotExists(baseDir, "logs");
        createDirIfNotExists(baseDir, "data");
        createDirIfNotExists(baseDir, "plugins");


        Path propertiesFile = baseDir.resolve("config").resolve("application.properties");
        if (Files.notExists(propertiesFile)) {
            String defaultConfig = """
                    # Default server configuration
                    server.motd=Welcome to PokeMeetup!
                    server.maxPlayers=20
                    # Add more server configs as needed
                    """;
            Files.writeString(propertiesFile, defaultConfig, StandardOpenOption.CREATE_NEW);
            logger.info("Created default application.properties at " + propertiesFile);
        }


        if (Files.notExists(runScript)) {
            String script = """
                    #!/bin/bash
                    # Simple startup script
                    # Assuming the jar is in the same directory as this script
                    java -jar pokemeetup-0.0.1-SNAPSHOT-server.jar 54555 54777
                    """;
            Files.writeString(runScript, script, StandardOpenOption.CREATE_NEW);
            runScript.toFile().setExecutable(true);
            logger.info("Created run.sh at " + runScript);
        }

        logger.info("Server deployment structure ensured at " + baseDir.toAbsolutePath());
    }

    private static void createDirIfNotExists(Path base, String name) throws IOException {
        Path dir = base.resolve(name);
        if (Files.notExists(dir)) {
            Files.createDirectories(dir);
        }
    }
}
