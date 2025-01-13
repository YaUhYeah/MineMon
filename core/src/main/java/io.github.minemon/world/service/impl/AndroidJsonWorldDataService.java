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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service("androidJsonWorldDataService")
@Primary
@Profile("android")
public class AndroidJsonWorldDataService extends JsonWorldDataService {
    private final Json json;

    private WorldService worldService;

    public void setWorldService(WorldService worldService) {
        this.worldService = worldService;
    }

    public AndroidJsonWorldDataService() {
        super("", true); // Base path will be handled by LibGDX
        this.json = new Json();
        this.json.setIgnoreUnknownFields(true);
    }

    private FileHandle getWorldFolder(String worldName) {
        return Gdx.files.external("Android/data/io.github.minemon/files/save/worlds/" + worldName);
    }

    private FileHandle getWorldFile(String worldName) {
        return getWorldFolder(worldName).child(worldName + ".json");
    }

    private FileHandle getChunkFile(String worldName, int chunkX, int chunkY) {
        return getWorldFolder(worldName).child("chunks").child(chunkX + "," + chunkY + ".json");
    }

    @Override
    public boolean worldExists(String worldName) {
        FileHandle folder = getWorldFolder(worldName);
        FileHandle worldFile = getWorldFile(worldName);
        return folder.exists() && worldFile.exists();
    }

    @Override
    public void loadWorld(String worldName, WorldData worldData) throws IOException {
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        if (worldData == null) {
            throw new IllegalArgumentException("WorldData instance cannot be null");
        }

        FileHandle worldFile = getWorldFile(worldName);
        if (!worldFile.exists()) {
            throw new IOException("World file not found: " + worldFile.path());
        }

        try {
            WorldData loaded = json.fromJson(WorldData.class, worldFile.readString());
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

    @Override
    public void saveWorld(WorldData worldData) throws IOException {
        if (worldData == null) {
            throw new IllegalArgumentException("WorldData cannot be null");
        }
        if (worldData.getWorldName() == null || worldData.getWorldName().isEmpty()) {
            throw new IllegalStateException("Cannot save a world with no name");
        }

        FileHandle folder = getWorldFolder(worldData.getWorldName());
        if (!folder.exists()) {
            folder.mkdirs();
        }

        FileHandle worldFile = getWorldFile(worldData.getWorldName());
        try {
            String jsonStr = json.toJson(worldData);
            worldFile.writeString(jsonStr, false);
            log.info("Successfully saved world data for '{}'", worldData.getWorldName());
        } catch (Exception e) {
            log.error("Error saving world '{}': {}", worldData.getWorldName(), e.getMessage());
            throw new IOException("Failed to save world", e);
        }
    }

    @Override
    public ChunkData loadChunk(String worldName, int chunkX, int chunkY) throws IOException {
        FileHandle file = getChunkFile(worldName, chunkX, chunkY);
        if (!file.exists()) {
            return null;
        }
        try {
            return json.fromJson(ChunkData.class, file.readString());
        } catch (Exception e) {
            log.error("Error loading chunk {},{} for world {}: {}", chunkX, chunkY, worldName, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveChunk(String worldName, ChunkData chunkData) throws IOException {
        synchronized (this) {
            FileHandle file = getChunkFile(worldName, chunkData.getChunkX(), chunkData.getChunkY());
            if (!file.parent().exists()) {
                file.parent().mkdirs();
            }
            
            String jsonStr = json.toJson(chunkData);
            file.writeString(jsonStr, false);
        }
    }

    @Override
    public List<String> listAllWorlds() {
        List<String> result = new ArrayList<>();
        FileHandle root = Gdx.files.external("Android/data/io.github.minemon/files/save/worlds");
        if (!root.exists()) {
            return result;
        }

        for (FileHandle dir : root.list()) {
            if (dir.isDirectory()) {
                String folderName = dir.name();
                FileHandle worldJson = dir.child(folderName + ".json");
                if (worldJson.exists()) {
                    result.add(folderName);
                }
            }
        }
        return result;
    }

    @Override
    public void deleteWorld(String worldName) {
        FileHandle folder = getWorldFolder(worldName);
        if (folder.exists()) {
            folder.deleteDirectory();
        }
    }

    @Override
    public void deleteChunk(String worldName, int chunkX, int chunkY) {
        FileHandle file = getChunkFile(worldName, chunkX, chunkY);
        if (file.exists()) {
            file.delete();
        }
    }
}