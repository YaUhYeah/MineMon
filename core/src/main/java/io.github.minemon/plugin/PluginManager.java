package io.github.minemon.plugin;

import io.github.minemon.event.EventBus;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

@Component
public class PluginManager {
    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);
    private final List<Plugin> plugins = new ArrayList<>();
    @Getter
    private final EventBus eventBus;

    public PluginManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    
    public void loadPlugins(Path pluginsDir) {
        if (Files.notExists(pluginsDir)) {
            try {
                Files.createDirectories(pluginsDir);
            } catch (IOException e) {
                logger.error("Failed to create plugins directory: {}", e.getMessage());
                return;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jarPath : stream) {
                loadPluginFromJar(jarPath);
            }
        } catch (IOException e) {
            logger.error("Error reading plugins directory: {}", e.getMessage());
        }
    }

    
    private void loadPluginFromJar(Path jarPath) {
        logger.info("Loading plugin from JAR: {}", jarPath);
        try {
            URL jarUrl = jarPath.toUri().toURL();
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, this.getClass().getClassLoader())) {
                ServiceLoader<Plugin> serviceLoader = ServiceLoader.load(Plugin.class, loader);
                for (Plugin plugin : serviceLoader) {
                    registerPlugin(plugin);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load plugin JAR {}: {}", jarPath, e.getMessage());
        }
    }

    
    public void registerPlugin(Plugin plugin) {
        plugins.add(plugin);
        plugin.onEnable();
        logger.info("Enabled plugin: {}", plugin.getClass().getName());
    }

    public void unloadAll() {
        for (Plugin plugin : plugins) {
            plugin.onDisable();
            logger.info("Disabled plugin: {}", plugin.getClass().getName());
        }
        plugins.clear();
    }
}
