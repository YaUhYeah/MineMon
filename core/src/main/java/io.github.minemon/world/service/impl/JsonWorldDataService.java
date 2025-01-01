package io.github.minemon.world.service.impl;

import com.badlogic.gdx.utils.Json;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.world.model.ChunkData;
import io.github.minemon.world.model.WorldData;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Now it references "saveDir" (by default "assets/save/worlds"),
 * ensuring we store everything in the same place used for icon.png
 */
@Slf4j
public class JsonWorldDataService {
    private final String baseWorldsDir;

    private final boolean isServer;
    private final Json json;
    private Path playerDataFolderPath(String worldName) {
        return worldFolderPath(worldName).resolve("playerdata");
    }

    @Autowired
    @Lazy
    private WorldService worldService;
    public void savePlayerData(String worldName, PlayerData playerData) throws IOException {
        // Only save if we're the server OR in singleplayer mode
        if (!isServer && worldService.isMultiplayerMode()) {
            log.debug("Skipping player data save in multiplayer client mode");
            return;
        }

        Path folder = playerDataFolderPath(worldName);
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }
        Path file = folder.resolve(playerData.getUsername() + ".json");

        if (isServer) {
            log.debug("Server saving player data for: {}", playerData.getUsername());
        } else {
            log.debug("Local save of player data in singleplayer mode for: {}", playerData.getUsername());
        }

        try (Writer w = Files.newBufferedWriter(file)) {
            json.toJson(playerData, w);
        }
    }

    public PlayerData loadPlayerData(String worldName, String username) throws IOException {
        Path folder = playerDataFolderPath(worldName);
        if (!Files.exists(folder)) {
            return null; // no folder at all
        }
        Path file = folder.resolve(username + ".json");
        if (!Files.exists(file)) {
            return null;
        }

        // Log for debugging
        log.info("Loading player data from {}", file.toAbsolutePath());

        try (Reader r = Files.newBufferedReader(file)) {
            return json.fromJson(PlayerData.class, r);
        }
    }


    public JsonWorldDataService(
            @Value("${world.saveDir:save/worlds/}") String baseWorldsDir,
            @Value("${server.isServer:false}") boolean isServer
    ) {
        this.isServer = isServer;
        log.info("JsonWorldDataService initialized with isServer={}", isServer);
        this.baseWorldsDir = baseWorldsDir;
        this.json = new Json();
        this.json.setIgnoreUnknownFields(true);
    }

    // Utility: build [assets/save/worlds + / + worldName] as a Path
    private Path worldFolderPath(String worldName) {
        return Paths.get(baseWorldsDir, worldName);
    }

    // The main world JSON file -> e.g. "assets/save/worlds/<worldName>/<worldName>.json"
    private Path worldFilePath(String worldName) {
        return worldFolderPath(worldName).resolve(worldName + ".json");
    }

    @SuppressWarnings("unused")
    public boolean worldExists(String worldName) {
        Path folder = worldFolderPath(worldName);
        Path worldFile = worldFilePath(worldName);
        return Files.exists(folder) && Files.exists(worldFile);
    }

    public void loadWorld(String worldName, WorldData worldData) throws IOException {
        Path worldFile = worldFilePath(worldName);
        if (!Files.exists(worldFile)) {
            throw new NoSuchFileException("World file not found: " + worldFile);
        }

        try (Reader reader = Files.newBufferedReader(worldFile)) {
            WorldData loaded = json.fromJson(WorldData.class, reader);
            worldData.setWorldName(loaded.getWorldName());
            worldData.setSeed(loaded.getSeed());
            worldData.setCreatedDate(loaded.getCreatedDate());
            worldData.setLastPlayed(loaded.getLastPlayed());
            worldData.setPlayedTime(loaded.getPlayedTime());
            worldData.getPlayers().clear();
            worldData.getPlayers().putAll(loaded.getPlayers());
            worldData.getChunks().clear();
            worldData.getChunks().putAll(loaded.getChunks());
        }
    }

    public void saveWorld(WorldData worldData) throws IOException {
        if (worldData.getWorldName() == null || worldData.getWorldName().isEmpty()) {
            throw new IllegalStateException("Cannot save a world with no name");
        }
        Path folder = worldFolderPath(worldData.getWorldName());
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }

        Path worldFile = worldFilePath(worldData.getWorldName());
        try (Writer writer = Files.newBufferedWriter(worldFile)) {
            json.toJson(worldData, writer);
        }
    }

    // -------------------------------------------------
    // Chunk storage: "assets/save/worlds/<worldName>/chunks/x,y.json"
    // -------------------------------------------------

    public ChunkData loadChunk(String worldName, int chunkX, int chunkY) throws IOException {
        Path p = chunkFilePath(worldName, chunkX, chunkY);
        if (!Files.exists(p)) {
            return null;
        }
        try (Reader r = Files.newBufferedReader(p)) {
            return json.fromJson(ChunkData.class, r);
        }
    }

    public void saveChunk(String worldName, ChunkData chunkData) throws IOException {
        synchronized (this) {
            Path p = chunkFilePath(worldName, chunkData.getChunkX(), chunkData.getChunkY());
            if (!Files.exists(p.getParent())) {
                Files.createDirectories(p.getParent());
            }
            // brand-new 'json' per call:
            Json localJson = new Json();
            localJson.setIgnoreUnknownFields(true);

            try (Writer writer = Files.newBufferedWriter(p)) {
                localJson.toJson(chunkData, writer);
            }
        }
    }


    private Path chunkFilePath(String worldName, int chunkX, int chunkY) {
        // e.g. "assets/save/worlds/<worldName>/chunks/<chunkX>,<chunkY>.json"
        return worldFolderPath(worldName)
                .resolve("chunks")
                .resolve(chunkX + "," + chunkY + ".json");
    }


    public List<String> listAllWorlds() {
        List<String> result = new ArrayList<>();
        Path root = Paths.get(baseWorldsDir);  // e.g. "assets/save/worlds"
        if (!Files.exists(root)) {
            return result;
        }
        try {
            Files.list(root)
                    .filter(Files::isDirectory)
                    .forEach(path -> {
                        // We check if <worldName>/<worldName>.json exists
                        String folderName = path.getFileName().toString();
                        Path worldJson = path.resolve(folderName + ".json");
                        if (Files.exists(worldJson)) {
                            result.add(folderName);
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not list worlds: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Delete entire world folder
     */
    public void deleteWorld(String worldName) {
        Path folder = worldFolderPath(worldName);
        if (!Files.exists(folder)) {
            return;
        }
        try {
            Files.walk(folder)
                    .sorted((p1, p2) -> p2.getNameCount() - p1.getNameCount()) // files first
                    .forEach(f -> {
                        try {
                            Files.delete(f);
                        } catch (IOException e) {
                            log.warn("Failed deleting {}", f);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to fully delete world '{}': {}", worldName, e.getMessage());
        }
    }

    /**
     * Delete a chunk JSON
     */
    public void deleteChunk(String worldName, int chunkX, int chunkY) {
        try {
            Path p = chunkFilePath(worldName, chunkX, chunkY);
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Failed to delete chunk {}/({},{})", worldName, chunkX, chunkY);
        }
    }
}
