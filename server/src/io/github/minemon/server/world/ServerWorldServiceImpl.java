package io.github.minemon.server.world;


import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
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
import io.github.minemon.world.service.*;
import io.github.minemon.world.service.impl.BaseWorldServiceImpl;
import io.github.minemon.world.service.impl.JsonWorldDataService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
@Primary
public class ServerWorldServiceImpl extends BaseWorldServiceImpl implements WorldService {
    private static final int TILE_SIZE = 32;
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_CACHE_SIZE = 256;
    private static final int AUTOSAVE_INTERVAL = 5 * 60 * 1000; 
    private final WorldGenerator worldGenerator;
    private final WorldObjectManager worldObjectManager;
    private final TileManager tileManager;
    private final BiomeConfigurationLoader biomeLoader;
    private final BiomeService biomeService;
    private final JsonWorldDataService jsonWorldDataService;
    private final WorldData worldData = new WorldData();
    private final Map<String, WorldData> loadedWorlds = new ConcurrentHashMap<>();
    private final Map<String, Object> chunkLocks = new ConcurrentHashMap<>();
    private final LoadingCache<String, ChunkData> chunkCache;
    private final ScheduledExecutorService autoSaveExecutor =
        Executors.newSingleThreadScheduledExecutor();
    private final Set<ChunkData> dirtyChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean initialized = false;
    @Value("${world.defaultName:defaultWorld}")
    private String defaultWorldName;
    private OrthographicCamera camera = null;
    @Autowired
    private ChunkLoadingManager chunkLoadingManager;


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
        this.chunkCache = CacheBuilder.newBuilder()
            .maximumSize(CHUNK_CACHE_SIZE)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .removalListener((RemovalNotification<String, ChunkData> notification) -> {
                
                try {
                    if (notification.getValue() != null) {
                        jsonWorldDataService.saveChunk("serverWorld", notification.getValue());
                    }
                } catch (IOException e) {
                    log.error("Failed to save evicted chunk: {}", e.getMessage());
                }
            })
            .build(new CacheLoader<>() {
                @Override
                public ChunkData load(String key) throws Exception {
                    String[] coords = key.split(",");
                    int chunkX = Integer.parseInt(coords[0]);
                    int chunkY = Integer.parseInt(coords[1]);
                    return Objects.requireNonNull(loadOrGenerateChunkInternal(chunkX, chunkY));
                }
            });
    }

    @PreDestroy
    public void shutdown() {
        
        performAutoSave();
        autoSaveExecutor.shutdown();
        try {
            if (!autoSaveExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                autoSaveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            autoSaveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void performAutoSave() {
        try {
            WorldData currentWorld = loadedWorlds.get("serverWorld");
            if (currentWorld != null) {
                
                jsonWorldDataService.saveWorld(currentWorld);

                
                for (Map.Entry<String, PlayerData> entry :
                    currentWorld.getPlayers().entrySet()) {
                    jsonWorldDataService.savePlayerData("serverWorld", entry.getValue());
                }

                
                for (ChunkData chunk : getDirtyChunks()) {
                    jsonWorldDataService.saveChunk("serverWorld", chunk);
                }

                log.info("Autosave completed successfully");
            }
        } catch (Exception e) {
            log.error("Autosave failed: ", e);
        }
    }

    private Collection<ChunkData> getDirtyChunks() {
        synchronized (dirtyChunks) {
            List<ChunkData> chunks = new ArrayList<>(dirtyChunks);
            dirtyChunks.clear();
            return chunks;
        }
    }

    @PostConstruct
    public void init() {
        autoSaveExecutor.scheduleAtFixedRate(
            this::performAutoSave,
            AUTOSAVE_INTERVAL,
            AUTOSAVE_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void updateWorldObjectState(WorldObjectUpdate update) {
        WorldData wd = loadedWorlds.get("serverWorld");
        if (wd == null) return;

        int chunkX = update.getTileX() / 16;
        int chunkY = update.getTileY() / 16;
        String chunkKey = chunkX + "," + chunkY;

        var chunkData = wd.getChunks().get(chunkKey);
        if (chunkData != null) {
            

            
            dirtyChunks.add(chunkData);

            try {
                jsonWorldDataService.saveChunk("serverWorld", chunkData);
            } catch (IOException e) {
                log.error("Failed to save chunk data: {}", e.getMessage());
            }
        }
    }

    @Override
    public void setPlayerData(PlayerData pd) {
        if (pd == null) return;

        WorldData wd = loadedWorlds.get("serverWorld");
        if (wd != null) {
            wd.getPlayers().put(pd.getUsername(), pd);
            try {
                jsonWorldDataService.savePlayerData("serverWorld", pd);

                
                int chunkX = (int) Math.floor(pd.getX() / 16);
                int chunkY = (int) Math.floor(pd.getY() / 16);
                markChunkDirty(chunkX, chunkY);
            } catch (IOException e) {
                log.error("Failed to save player data: {}", e.getMessage());
            }
        }
    }

    private void markChunkDirty(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        ChunkData chunk = worldData.getChunks().get(key);
        if (chunk != null) {
            dirtyChunks.add(chunk);
        }
    }

    public ChunkData loadOrGenerateChunkInternal(int chunkX, int chunkY) {
        try {
            
            if (!initialized) {
                throw new IllegalStateException("WorldService not initialized");
            }

            if (worldGenerator == null || biomeService == null) {
                throw new IllegalStateException("Required services not initialized");
            }

            
            String lockKey = chunkX + "," + chunkY;
            synchronized (chunkLocks.computeIfAbsent(lockKey, k -> new Object())) {
                
                ChunkData loaded = jsonWorldDataService.loadChunk("serverWorld", chunkX, chunkY);
                if (loaded != null) {
                    return loaded;
                }

                
                int[][] tiles = worldGenerator.generateChunk(chunkX, chunkY);
                if (tiles == null) {
                    throw new RuntimeException("Failed to generate tiles for chunk");
                }

                ChunkData newChunk = new ChunkData();
                newChunk.setChunkX(chunkX);
                newChunk.setChunkY(chunkY);
                newChunk.setTiles(tiles);

                
                Biome biome = worldGenerator.getBiomeForChunk(chunkX, chunkY);
                if (biome == null) {
                    throw new RuntimeException("Failed to get biome for chunk");
                }

                
                List<WorldObject> objects = worldObjectManager.generateObjectsForChunk(
                    chunkX, chunkY, tiles, biome, getWorldData().getSeed());
                newChunk.setObjects(objects);

                
                try {
                    jsonWorldDataService.saveChunk("serverWorld", newChunk);
                } catch (IOException e) {
                    log.error("Failed to save generated chunk: {}", e.getMessage());
                }

                return newChunk;
            }
        } catch (Exception e) {
            log.error("Error generating chunk {},{}: {}", chunkX, chunkY, e.getMessage(), e);
            throw new RuntimeException("Chunk generation failed", e);
        }
    }

    @PostConstruct
    public void validateConfiguration() {
        if (worldGenerator == null) {
            throw new IllegalStateException("WorldGenerator not configured");
        }
        if (biomeService == null) {
            throw new IllegalStateException("BiomeService not configured");
        }

        
        Path configDir = Paths.get("config");
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                throw new IllegalStateException("Could not create config directory", e);
            }
        }

        
        Path biomesConfig = configDir.resolve("biomes.json");
        if (!Files.exists(biomesConfig)) {
            throw new IllegalStateException("biomes.json configuration file missing");
        }
    }

    @Override
    public void forceLoadChunksAt(float tileX, float tileY) {
        
        int RADIUS = 2;
        int chunkX = (int) Math.floor(tileX / CHUNK_SIZE);
        int chunkY = (int) Math.floor(tileY / CHUNK_SIZE);

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                int cx = chunkX + dx;
                int cy = chunkY + dy;
                Vector2 chunkPos = new Vector2(cx, cy);

                if (!isChunkLoaded(chunkPos)) {
                    if (isMultiplayerMode()) {
                        
                        
                        chunkLoadingManager.queueChunkRequest(cx, cy, true );
                    } else {
                        
                        loadOrGenerateChunk(cx, cy);
                    }
                }
            }
        }
    }


    @Override
    public void update(float delta) {

    }

    @Override
    public void initIfNeeded() {
        if (initialized) {
            return;
        }

        
        Map<BiomeType, Biome> biomes = biomeLoader.loadBiomes("config/biomes.json");
        if (biomes.isEmpty()) {
            log.error("Failed to load biome configurations");
            return;
        }

        
        if (!loadedWorlds.containsKey("serverWorld")) {
            try {
                WorldData wd = new WorldData();
                jsonWorldDataService.loadWorld("serverWorld", wd);
                loadedWorlds.put("serverWorld", wd);
                worldGenerator.setSeedAndBiomes(wd.getSeed(), biomes);
                biomeService.initWithSeed(wd.getSeed());
            } catch (IOException e) {
                
                WorldData newWorld = new WorldData();
                newWorld.setWorldName("serverWorld");
                newWorld.setSeed(new Random().nextLong()); 
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
    public void handleDisconnect() {

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
    public void clearWorldData() {
        worldData.getChunks().clear();
        worldData.getPlayers().clear();
        worldData.setWorldName(null);
        worldData.setSeed(0);
        worldData.setCreatedDate(0);
        worldData.setLastPlayed(0);
        worldData.setPlayedTime(0);
        initialized = false;
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
        
        jsonWorldDataService.deleteChunk(worldData.getWorldName(), chunkX, chunkY);
        loadOrGenerateChunk(chunkX, chunkY);
    }

    @Override
    public void generateWorldThumbnail(String worldName) {
        log.info("Skipping world thumbnail generation on server.");
    }

    @Override
    public void preloadChunksAroundPosition(float i, float i1) {

    }

    @Override
    public ChunkData loadOrGenerateChunk(int chunkX, int chunkY) {
        try {
            if (!initialized) {
                initIfNeeded();
            }
            String key = chunkX + "," + chunkY;

            
            ChunkData loaded = jsonWorldDataService.loadChunk("serverWorld", chunkX, chunkY);
            if (loaded != null) {
                
                worldData.getChunks().put(key, loaded);
                return loaded;
            }

            
            int[][] tiles = worldGenerator.generateChunk(chunkX, chunkY);
            Biome biome = worldGenerator.getBiomeForChunk(chunkX, chunkY);

            ChunkData newChunk = new ChunkData();
            newChunk.setChunkX(chunkX);
            newChunk.setChunkY(chunkY);
            newChunk.setTiles(tiles);

            List<WorldObject> objects = worldObjectManager.generateObjectsForChunk(
                chunkX, chunkY, tiles, biome, getWorldData().getSeed()
            );
            newChunk.setObjects(objects);

            
            jsonWorldDataService.saveChunk("serverWorld", newChunk);

            
            worldData.getChunks().put(key, newChunk);

            return newChunk;

        } catch (Exception e) {
            log.error("Failed to load/generate chunk {},{}: {}", chunkX, chunkY, e.getMessage(), e);
            throw new RuntimeException("Chunk processing failed", e);
        }
    }


    @Override
    public void loadOrReplaceChunkData(int chunkX, int chunkY, int[][] tiles, List<WorldObject> objects) {
        
        log.warn("Client attempted to send chunk data to server - ignoring");

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
