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


@Slf4j
public class JsonWorldDataService {
    private final String baseWorldsDir;

    private final boolean isServer;
    private final Json json;
    @Autowired
    @Lazy
    private WorldService worldService;

    public JsonWorldDataService(String baseWorldsDir, boolean isServer) {
        // For Android, use the external files directory
        if (isAndroid()) {
            this.baseWorldsDir = "Android/data/io.github.minemon/files/save/worlds";
        } else {
            String defaultDir = System.getProperty("user.home", ".") + "/save/worlds";
            this.baseWorldsDir = baseWorldsDir != null ? baseWorldsDir.trim() : defaultDir;
        }
        
        if (this.baseWorldsDir.isEmpty()) {
            throw new IllegalArgumentException("Base worlds directory cannot be empty");
        }
        this.isServer = isServer;
        this.json = new Json();
        this.json.setIgnoreUnknownFields(true);
    }

    private boolean isAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }



    private Path playerDataFolderPath(String worldName) {
        return worldFolderPath(worldName).resolve("playerdata");
    }

    public void savePlayerData(String worldName, PlayerData playerData) throws IOException {
        if (worldName == null || playerData == null || playerData.getUsername() == null) {
            log.warn("Attempted to save invalid player data");
            return;
        }

        Path folder = playerDataFolderPath(worldName);
        Files.createDirectories(folder);

        Path file = folder.resolve(playerData.getUsername() + ".json");
        try (Writer w = Files.newBufferedWriter(file)) {
            json.toJson(playerData, w);
        }
    }

    public PlayerData loadPlayerData(String worldName, String username) throws IOException {
        if (worldName == null || username == null) {
            log.warn("Attempted to load player data with null world name or username");
            return null;
        }

        Path folder = playerDataFolderPath(worldName);
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
            return null;
        }

        Path file = folder.resolve(username + ".json");
        if (!Files.exists(file)) {
            return null;
        }

        try (Reader r = Files.newBufferedReader(file)) {
            return json.fromJson(PlayerData.class, r);
        } catch (Exception e) {
            log.error("Error loading player data for {}: {}", username, e.getMessage());
            return null;
        }
    }
    private Path worldFolderPath(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        
        if (isAndroid()) {
            // On Android, use LibGDX's FileHandle
            FileHandle folder = Gdx.files.external(baseWorldsDir + "/" + worldName.trim());
            if (!folder.exists()) {
                folder.mkdirs();
            }
            return Paths.get(folder.path());
        } else {
            return Paths.get(baseWorldsDir, worldName.trim());
        }
    }
    
    private Path worldFilePath(String worldName) {
        if (isAndroid()) {
            FileHandle file = Gdx.files.external(baseWorldsDir + "/" + worldName.trim() + "/" + worldName + ".json");
            return Paths.get(file.path());
        } else {
            return worldFolderPath(worldName).resolve(worldName + ".json");
        }
    }

    @SuppressWarnings("unused")
    public boolean worldExists(String worldName) {
        if (isAndroid()) {
            FileHandle folder = Gdx.files.external(baseWorldsDir + "/" + worldName.trim());
            FileHandle worldFile = folder.child(worldName + ".json");
            return folder.exists() && worldFile.exists();
        } else {
            Path folder = worldFolderPath(worldName);
            Path worldFile = worldFilePath(worldName);
            return Files.exists(folder) && Files.exists(worldFile);
        }
    }

    public void loadWorld(String worldName, WorldData worldData) throws IOException {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        if (worldData == null) {
            throw new IllegalArgumentException("WorldData instance cannot be null");
        }

        Path worldFile = worldFilePath(worldName);
        if (!Files.exists(worldFile)) {
            throw new NoSuchFileException("World file not found: " + worldFile);
        }

        try (Reader reader = Files.newBufferedReader(worldFile)) {
            WorldData loaded = json.fromJson(WorldData.class, reader);
            if (loaded == null) {
                throw new IOException("Failed to parse world data for " + worldName);
            }

            
            worldData.setWorldName(loaded.getWorldName());
            worldData.setSeed(loaded.getSeed());
            worldData.setCreatedDate(loaded.getCreatedDate());
            worldData.setLastPlayed(loaded.getLastPlayed());
            worldData.setPlayedTime(loaded.getPlayedTime());

            
            worldData.getPlayers().clear();
            worldData.getPlayers().putAll(loaded.getPlayers());
            worldData.getChunks().clear();
            worldData.getChunks().putAll(loaded.getChunks());

            log.info("Successfully loaded world data for '{}'", worldName);
        } catch (Exception e) {
            log.error("Error loading world '{}': {}", worldName, e.getMessage());
            throw new IOException("Failed to load world: " + worldName, e);
        }
    }

    public void saveWorld(WorldData worldData) throws IOException {
        if (worldData == null) {
            throw new IllegalArgumentException("WorldData cannot be null");
        }
        if (worldData.getWorldName() == null || worldData.getWorldName().isEmpty()) {
            throw new IllegalStateException("Cannot save a world with no name");
        }

        if (isAndroid()) {
            FileHandle folder = Gdx.files.external(baseWorldsDir + "/" + worldData.getWorldName().trim());
            if (!folder.exists()) {
                folder.mkdirs();
            }

            FileHandle worldFile = folder.child(worldData.getWorldName() + ".json");
            try {
                String jsonStr = json.toJson(worldData);
                worldFile.writeString(jsonStr, false);
                log.info("Successfully saved world data for '{}'", worldData.getWorldName());
            } catch (Exception e) {
                log.error("Error saving world '{}': {}", worldData.getWorldName(), e.getMessage());
                throw new IOException("Failed to save world", e);
            }
        } else {
            Path folder = worldFolderPath(worldData.getWorldName());
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }

            Path worldFile = worldFilePath(worldData.getWorldName());
            try (Writer writer = Files.newBufferedWriter(worldFile)) {
                json.toJson(worldData, writer);
                log.info("Successfully saved world data for '{}'", worldData.getWorldName());
            } catch (Exception e) {
                log.error("Error saving world '{}': {}", worldData.getWorldName(), e.getMessage());
                throw new IOException("Failed to save world", e);
            }
        }
    }

    
    
    

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
            
            Json localJson = new Json();
            localJson.setIgnoreUnknownFields(true);

            try (Writer writer = Files.newBufferedWriter(p)) {
                localJson.toJson(chunkData, writer);
            }
        }
    }


    private Path chunkFilePath(String worldName, int chunkX, int chunkY) {
        
        return worldFolderPath(worldName)
            .resolve("chunks")
            .resolve(chunkX + "," + chunkY + ".json");
    }


    public List<String> listAllWorlds() {
        List<String> result = new ArrayList<>();
        Path root = Paths.get(baseWorldsDir);  
        if (!Files.exists(root)) {
            return result;
        }
        try {
            Files.list(root)
                .filter(Files::isDirectory)
                .forEach(path -> {
                    
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

    
    public void deleteWorld(String worldName) {
        Path folder = worldFolderPath(worldName);
        if (!Files.exists(folder)) {
            return;
        }
        try {
            Files.walk(folder)
                .sorted((p1, p2) -> p2.getNameCount() - p1.getNameCount()) 
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

    
    public void deleteChunk(String worldName, int chunkX, int chunkY) {
        try {
            Path p = chunkFilePath(worldName, chunkX, chunkY);
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Failed to delete chunk {}/({},{})", worldName, chunkX, chunkY);
        }
    }
}
