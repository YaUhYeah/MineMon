package io.github.minemon.server.world;


import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeType;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.model.ChunkData;
import io.github.minemon.world.model.ObjectType;
import io.github.minemon.world.model.WorldData;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.TileManager;
import io.github.minemon.world.service.WorldGenerator;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import io.github.minemon.world.service.impl.BaseWorldServiceImpl;
import io.github.minemon.world.service.impl.JsonWorldDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@Primary
public class ServerWorldServiceImpl extends BaseWorldServiceImpl implements WorldService {
    private static final int TILE_SIZE = 32;
    private static final int CHUNK_SIZE = 16;

    private final WorldGenerator worldGenerator;
    private final WorldObjectManager worldObjectManager;
    private final TileManager tileManager;
    private final BiomeConfigurationLoader biomeLoader;
    private final BiomeService biomeService;
    private final JsonWorldDataService jsonWorldDataService;

    private final WorldData worldData = new WorldData();
    private final Map<String, WorldData> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<String, Object> chunkLocks = new ConcurrentHashMap<>();
    private boolean initialized = false;
    @Value("${world.defaultName:defaultWorld}")
    private String defaultWorldName;
    private OrthographicCamera camera = null;

    public ServerWorldServiceImpl(
        WorldGenerator worldGenerator,
        WorldObjectManager worldObjectManager,
        TileManager tileManager,
        BiomeConfigurationLoader biomeLoader, BiomeService biomeService,
        @Qualifier("serverJsonWorldDataService") JsonWorldDataService jsonWorldDataService
    ) {
        this.worldGenerator = worldGenerator;
        this.biomeService = biomeService;
        this.worldObjectManager = worldObjectManager;
        this.tileManager = tileManager;
        this.biomeLoader = biomeLoader;
        this.jsonWorldDataService = jsonWorldDataService;
    }

    @Override
    public void initIfNeeded() {
        if (initialized) {
            return;
        }

        // Initialize biome configuration first
        Map<BiomeType, Biome> biomes = biomeLoader.loadBiomes("config/biomes.json");
        if (biomes.isEmpty()) {
            log.error("Failed to load biome configurations");
            return;
        }

        // Set seed and biomes for world generator
        if (!loadedWorlds.containsKey("serverWorld")) {
            try {
                WorldData wd = new WorldData();
                jsonWorldDataService.loadWorld("serverWorld", wd);
                loadedWorlds.put("serverWorld", wd);
                worldGenerator.setSeedAndBiomes(wd.getSeed(), biomes);
                biomeService.initWithSeed(wd.getSeed());
            } catch (IOException e) {
                // Create new world if none exists
                WorldData newWorld = new WorldData();
                newWorld.setWorldName("serverWorld");
                newWorld.setSeed(new Random().nextLong()); // Generate new seed
                try {
                    jsonWorldDataService.saveWorld(newWorld);
                    worldGenerator.setSeedAndBiomes(newWorld.getSeed(), biomes);
                    biomeService.initWithSeed(newWorld.getSeed());
                } catch (IOException ex) {
                    log.error("Failed to save new server world", ex);
                }
                loadedWorlds.put("serverWorld", newWorld);
            }
        }

        initialized = true;
        log.info("ServerWorldService initialized with seed {}",
            loadedWorlds.get("serverWorld").getSeed());
    }

    @Override
    public WorldData getWorldData() {
        return worldData;
    }

    @Override
    public boolean isMultiplayerMode() {
        return true;
    }

    @Override
    public void setMultiplayerMode(boolean multiplayer) {

    }

    @Override
    public TileManager getTileManager() {
        return tileManager;
    }

    // ------------------------------
    // Saving and Loading from JSON
    // ------------------------------
    @Override
    public void loadWorldData() {
        try {
            jsonWorldDataService.loadWorld(defaultWorldName, worldData);
            initIfNeeded();
            log.info("Loaded default world data for '{}' from JSON (server)", defaultWorldName);
        } catch (IOException e) {
            log.warn("Failed to load default world '{}': {}", defaultWorldName, e.getMessage());
        }
    }

    @Override
    public boolean createWorld(String worldName, long seed) {
        // We simply set in-memory and call save
        if (jsonWorldDataService.worldExists(worldName)) {
            log.warn("World '{}' already exists, cannot create", worldName);
            return false;
        }

        long now = System.currentTimeMillis();
        worldData.setWorldName(worldName);
        worldData.setSeed(seed);
        worldData.setCreatedDate(now);
        worldData.setLastPlayed(now);
        worldData.setPlayedTime(0);

        try {
            jsonWorldDataService.saveWorld(worldData);
        } catch (IOException e) {
            log.error("Failed to create new world '{}': {}", worldName, e.getMessage());
            return false;
        }
        log.info("Created new world '{}' with seed {} in JSON (server)", worldName, seed);
        return true;
    }

    @Override
    public void saveWorldData() {
        WorldData wd = loadedWorlds.get("serverWorld");
        if (wd != null) {
            try {
                jsonWorldDataService.saveWorld(wd);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void loadWorld(String worldName) {
        try {
            jsonWorldDataService.loadWorld(worldName, worldData);
            initIfNeeded();
            log.info("Loaded world data for '{}' from JSON (server)", worldName);
        } catch (IOException e) {
            log.warn("World '{}' does not exist in JSON or failed to load: {}", worldName, e.getMessage());
        }
    }

    public ChunkData loadOrGenerateChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;

        // Get or create chunk lock
        Object chunkLock = chunkLocks.computeIfAbsent(key, k -> new Object());

        synchronized (chunkLock) {
            try {
                // Try to get existing chunk first
                ChunkData chunkData = worldData.getChunks().get(key);
                if (chunkData != null) {
                    return chunkData;
                }

                // Try to load from storage
                try {
                    ChunkData loaded = jsonWorldDataService.loadChunk("serverWorld", chunkX, chunkY);
                    if (loaded != null) {
                        worldObjectManager.loadObjectsForChunk(chunkX, chunkY, loaded.getObjects());
                        worldData.getChunks().put(key, loaded);
                        return loaded;
                    }
                } catch (IOException e) {
                    log.warn("Could not load chunk {}: {}", key, e.getMessage());
                }

                // Generate new chunk
                int[][] tiles = worldGenerator.generateChunk(chunkX, chunkY);
                if (tiles == null) {
                    log.error("Failed to generate tiles for chunk {}", key);
                    return null;
                }

                ChunkData newChunk = new ChunkData();
                newChunk.setChunkX(chunkX);
                newChunk.setChunkY(chunkY);
                newChunk.setTiles(tiles);

                // Generate objects
                Biome biome = worldGenerator.getBiomeForChunk(chunkX, chunkY);
                List<WorldObject> objects = worldObjectManager.generateObjectsForChunk(
                    chunkX, chunkY, tiles, biome, worldData.getSeed());
                newChunk.setObjects(objects);

                // Save to memory and disk
                worldData.getChunks().put(key, newChunk);
                try {
                    jsonWorldDataService.saveChunk("serverWorld", newChunk);
                    log.info("Generated and saved new chunk {} with {} objects",
                        key, objects.size());
                } catch (IOException e) {
                    log.error("Failed to save generated chunk {}: {}", key, e.getMessage());
                }

                return newChunk;
            } catch (Exception e) {
                log.error("Error generating chunk {}: {}", key, e.getMessage(), e);
                return null;
            }
        }
    }

    @Override
    public int[][] getChunkTiles(int chunkX, int chunkY) {
        ChunkData chunk = loadOrGenerateChunk(chunkX, chunkY);
        return chunk != null ? chunk.getTiles() : null;
    }

    @Override
    public boolean isChunkLoaded(Vector2 chunkPos) {
        String key = String.format("%d,%d", (int) chunkPos.x, (int) chunkPos.y);
        return worldData.getChunks().containsKey(key);
    }

    @Override
    public void loadChunk(Vector2 chunkPos) {
        loadOrGenerateChunk((int) chunkPos.x, (int) chunkPos.y);
    }

    @Override
    public List<WorldObject> getVisibleObjects(Rectangle viewBounds) {
        // Implementation unchanged, except no DB call
        List<WorldObject> visibleObjects = new ArrayList<>();
        Map<String, ChunkData> visibleChunks = getVisibleChunks(viewBounds);
        for (ChunkData chunk : visibleChunks.values()) {
            if (chunk.getObjects() != null) {
                visibleObjects.addAll(chunk.getObjects());
            }
        }
        return visibleObjects;
    }

    @Override
    public Map<String, ChunkData> getVisibleChunks(Rectangle viewBounds) {
        // Implementation is basically the same
        Map<String, ChunkData> visibleChunks = new HashMap<>();

        int startChunkX = (int) Math.floor(viewBounds.x / (CHUNK_SIZE * TILE_SIZE));
        int startChunkY = (int) Math.floor(viewBounds.y / (CHUNK_SIZE * TILE_SIZE));
        int endChunkX = (int) Math.ceil((viewBounds.x + viewBounds.width) / (CHUNK_SIZE * TILE_SIZE));
        int endChunkY = (int) Math.ceil((viewBounds.y + viewBounds.height) / (CHUNK_SIZE * TILE_SIZE));

        for (int x = startChunkX; x <= endChunkX; x++) {
            for (int y = startChunkY; y <= endChunkY; y++) {
                String key = x + "," + y;
                if (!worldData.getChunks().containsKey(key)) {
                    loadOrGenerateChunk(x, y);
                }
                ChunkData chunk = worldData.getChunks().get(key);
                if (chunk != null) {
                    visibleChunks.put(key, chunk);
                }
            }
        }

        return visibleChunks;
    }


    @Override
    public void setPlayerData(PlayerData pd) {
        if (pd == null) return;

        log.debug("Setting player data for {}: pos=({},{}), moving={}",
            pd.getUsername(), pd.getX(), pd.getY(), pd.isMoving());

        // Get previous state if exists
        PlayerData oldData = getPlayerData(pd.getUsername());
        if (oldData != null) {
            float dx = Math.abs(oldData.getX() - pd.getX());
            float dy = Math.abs(oldData.getY() - pd.getY());
            boolean posChanged = dx > 0.001f || dy > 0.001f;

            log.debug("Position changed={}, dx={}, dy={}", posChanged, dx, dy);
            pd.setMoving(posChanged);
        }

        WorldData wd = loadedWorlds.get("serverWorld");
        if (wd != null) {
            wd.getPlayers().put(pd.getUsername(), pd);
            try {
                jsonWorldDataService.savePlayerData("serverWorld", pd);
            } catch (IOException e) {
                log.error("Failed to save player data: {}", e.getMessage());
            }
        }
    }

    @Override
    public PlayerData getPlayerData(String username) {
        WorldData wd = loadedWorlds.get("serverWorld");
        if (wd == null) return null;
        PlayerData existing = wd.getPlayers().get(username);
        if (existing != null) return existing;
        try {
            PlayerData pd = jsonWorldDataService.loadPlayerData("serverWorld", username);
            if (pd != null) {
                wd.getPlayers().put(username, pd);
            }
            return pd;
        } catch (IOException e) {
            return null;
        }
    }


    @Override
    public List<String> getAvailableWorlds() {
        return jsonWorldDataService.listAllWorlds();
    }

    @Override
    public void deleteWorld(String worldName) {
        if (!jsonWorldDataService.worldExists(worldName)) {
            log.warn("World '{}' does not exist in JSON, cannot delete (server)", worldName);
            return;
        }

        jsonWorldDataService.deleteWorld(worldName);

        // If it’s the currently loaded world, clear it
        if (worldData.getWorldName() != null && worldData.getWorldName().equals(worldName)) {
            worldData.setWorldName(null);
            worldData.setSeed(0);
            worldData.getPlayers().clear();
            worldData.getChunks().clear();
            worldData.setCreatedDate(0);
            worldData.setLastPlayed(0);
            worldData.setPlayedTime(0);
            log.info("Cleared current loaded world data because it was deleted (server).");
        }
        log.info("Deleted world '{}' from JSON (server)", worldName);
    }

    @Override
    public void regenerateChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        worldData.getChunks().remove(key);
        // Also delete chunk JSON if present
        jsonWorldDataService.deleteChunk(worldData.getWorldName(), chunkX, chunkY);
        loadOrGenerateChunk(chunkX, chunkY);
    }

    @Override
    public void generateWorldThumbnail(String worldName) {
        log.info("Skipping world thumbnail generation on server.");
    }


    @Override
    public void loadOrReplaceChunkData(int chunkX, int chunkY, int[][] tiles, List<WorldObject> objects) {
        // Server should generate its own chunks, not accept them from clients
        log.warn("Client attempted to send chunk data to server - ignoring");

    }

    @Override
    public void updateWorldObjectState(WorldObjectUpdate update) {
        // Example: move or remove an object in the chunk
        WorldData wd = loadedWorlds.get("serverWorld");
        if (wd == null) return;
        String chunkKey = (update.getTileX() / 16) + "," + (update.getTileY() / 16);

        var chunkData = wd.getChunks().get(chunkKey);
        if (chunkData != null) {
            if (update.isRemoved()) {
                chunkData.getObjects().removeIf(o -> o.getId().equals(update.getObjectId()));
            } else {
                // Possibly find or create
                var existing = chunkData.getObjects().stream()
                    .filter(o -> o.getId().equals(update.getObjectId()))
                    .findFirst();
                if (existing.isPresent()) {
                    // update position
                    var wo = existing.get();
                    wo.setTileX(update.getTileX());
                    wo.setTileY(update.getTileY());
                } else {
                    // create new
                    WorldObject obj = new WorldObject(
                        update.getTileX(),
                        update.getTileY(),
                        // parse from update.getType()
                        ObjectType.valueOf(update.getType()),
                        true // or read from config
                    );
                    obj.setId(update.getObjectId());
                    chunkData.getObjects().add(obj);
                }
            }
        }
        // Optionally save chunk to disk
        try {
            jsonWorldDataService.saveChunk("serverWorld", chunkData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public OrthographicCamera getCamera() {
        return camera;
    }

    @Override
    public void setCamera(OrthographicCamera camera) {
        this.camera = camera;
    }
}
