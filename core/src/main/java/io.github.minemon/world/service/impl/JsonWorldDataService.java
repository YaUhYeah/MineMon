package io.github.minemon.world.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.world.model.ChunkData;
import io.github.minemon.world.model.WorldData;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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



    private FileHandle getPlayerDataFolder(String worldName) {
        if (isAndroid()) {
            return Gdx.files.external(baseWorldsDir + "/" + worldName + "/playerdata");
        } else {
            return Gdx.files.absolute(playerDataFolderPath(worldName).toString());
        }
    }

    private FileHandle getPlayerDataFile(String worldName, String username) {
        return getPlayerDataFolder(worldName).child(username + ".json");
    }

    public void savePlayerData(String worldName, PlayerData playerData) throws IOException {
        if (worldName == null || playerData == null || playerData.getUsername() == null) {
            log.warn("Attempted to save invalid player data");
            return;
        }

        FileHandle folder = getPlayerDataFolder(worldName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        FileHandle file = getPlayerDataFile(worldName, playerData.getUsername());
        try {
            String jsonStr = json.toJson(playerData);
            file.writeString(jsonStr, false);
            log.info("Successfully saved player data for '{}' in world '{}'", playerData.getUsername(), worldName);
        } catch (Exception e) {
            log.error("Error saving player data for '{}' in world '{}': {}", playerData.getUsername(), worldName, e.getMessage());
            throw new IOException("Failed to save player data", e);
        }
    }

    public PlayerData loadPlayerData(String worldName, String username) throws IOException {
        if (worldName == null || username == null) {
            log.warn("Attempted to load player data with null world name or username");
            return null;
        }

        FileHandle folder = getPlayerDataFolder(worldName);
        if (!folder.exists()) {
            folder.mkdirs();
            return null;
        }

        FileHandle file = getPlayerDataFile(worldName, username);
        if (!file.exists()) {
            return null;
        }

        try {
            String jsonStr = file.readString();
            PlayerData data = json.fromJson(PlayerData.class, jsonStr);
            log.info("Successfully loaded player data for '{}' in world '{}'", username, worldName);
            return data;
        } catch (Exception e) {
            log.error("Error loading player data for '{}' in world '{}': {}", username, worldName, e.getMessage());
            return null;
        }
    }

    private Path playerDataFolderPath(String worldName) {
        return worldFolderPath(worldName).resolve("playerdata");
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

        FileHandle worldFile;
        if (isAndroid()) {
            worldFile = Gdx.files.external(baseWorldsDir + "/" + worldName + "/" + worldName + ".json");
        } else {
            worldFile = Gdx.files.absolute(worldFilePath(worldName).toString());
        }

        if (!worldFile.exists()) {
            throw new NoSuchFileException("World file not found: " + worldFile.path());
        }

        try {
            String jsonContent = worldFile.readString();
            WorldData loaded = json.fromJson(WorldData.class, jsonContent);
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

        FileHandle folder;
        if (isAndroid()) {
            folder = Gdx.files.external(baseWorldsDir + "/" + worldData.getWorldName().trim());
        } else {
            folder = Gdx.files.absolute(worldFolderPath(worldData.getWorldName()).toString());
        }

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
    }





    private FileHandle getChunkFile(String worldName, int chunkX, int chunkY) {
        if (isAndroid()) {
            FileHandle worldFolder = Gdx.files.external(baseWorldsDir + "/" + worldName);
            return worldFolder.child("chunks").child(chunkX + "," + chunkY + ".json");
        } else {
            return Gdx.files.absolute(chunkFilePath(worldName, chunkX, chunkY).toString());
        }
    }

    public ChunkData loadChunk(String worldName, int chunkX, int chunkY) throws IOException {
        FileHandle chunkFile = getChunkFile(worldName, chunkX, chunkY);
        if (!chunkFile.exists()) {
            return null;
        }
        try {
            return json.fromJson(ChunkData.class, chunkFile.readString());
        } catch (Exception e) {
            log.error("Error loading chunk {},{} for world {}: {}", chunkX, chunkY, worldName, e.getMessage());
            return null;
        }
    }

    public void saveChunk(String worldName, ChunkData chunkData) throws IOException {
        synchronized (this) {
            FileHandle chunkFile = getChunkFile(worldName, chunkData.getChunkX(), chunkData.getChunkY());
            if (!chunkFile.parent().exists()) {
                chunkFile.parent().mkdirs();
            }

            String jsonStr = json.toJson(chunkData);
            chunkFile.writeString(jsonStr, false);
        }
    }

    private Path chunkFilePath(String worldName, int chunkX, int chunkY) {
        return worldFolderPath(worldName)
            .resolve("chunks")
            .resolve(chunkX + "," + chunkY + ".json");
    }


    public List<String> listAllWorlds() {
        List<String> result = new ArrayList<>();
        FileHandle root;
        if (isAndroid()) {
            root = Gdx.files.external(baseWorldsDir);
        } else {
            root = Gdx.files.absolute(baseWorldsDir);
        }

        if (!root.exists()) {
            log.info("Worlds directory does not exist: {}", root.path());
            return result;
        }

        try {
            for (FileHandle dir : root.list()) {
                if (dir.isDirectory()) {
                    String folderName = dir.name();
                    FileHandle worldJson = dir.child(folderName + ".json");
                    if (worldJson.exists()) {
                        result.add(folderName);
                        log.debug("Found world: {}", folderName);
                    }
                }
            }
            log.info("Found {} worlds in {}", result.size(), root.path());
        } catch (Exception e) {
            log.warn("Could not list worlds in {}: {}", root.path(), e.getMessage());
        }
        return result;
    }

    public void deleteWorld(String worldName) {
        FileHandle folder;
        if (isAndroid()) {
            folder = Gdx.files.external(baseWorldsDir + "/" + worldName);
        } else {
            folder = Gdx.files.absolute(worldFolderPath(worldName).toString());
        }

        if (!folder.exists()) {
            log.debug("World '{}' does not exist at {}", worldName, folder.path());
            return;
        }

        try {
            folder.deleteDirectory();
            log.info("Successfully deleted world '{}' at {}", worldName, folder.path());
        } catch (Exception e) {
            log.warn("Failed to delete world '{}' at {}: {}", worldName, folder.path(), e.getMessage());
        }
    }

    public void deleteChunk(String worldName, int chunkX, int chunkY) {
        FileHandle chunkFile = getChunkFile(worldName, chunkX, chunkY);
        if (chunkFile.exists()) {
            try {
                chunkFile.delete();
                log.debug("Deleted chunk {},{} in world '{}'", chunkX, chunkY, worldName);
            } catch (Exception e) {
                log.warn("Failed to delete chunk {},{} in world '{}': {}", chunkX, chunkY, worldName, e.getMessage());
            }
        }
    }
}
