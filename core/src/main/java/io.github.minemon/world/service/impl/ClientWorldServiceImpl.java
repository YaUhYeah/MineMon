package io.github.minemon.world.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeType;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.config.WorldConfig;
import io.github.minemon.world.model.*;
import io.github.minemon.world.service.TileManager;
import io.github.minemon.world.service.WorldGenerator;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class ClientWorldServiceImpl extends BaseWorldServiceImpl implements WorldService {
    private static final int CHUNK_SIZE = 16;
    private static final int TILE_SIZE = 32;
    private final boolean isServer = false;
    private final WorldGenerator worldGenerator;
    @Autowired

    @Lazy
    private final WorldObjectManager worldObjectManager;
    private final TileManager tileManager;
    private final ObjectTextureManager objectTextureManager;
    private final BiomeConfigurationLoader biomeLoader;
    private final BiomeService biomeService;

    private final JsonWorldDataService jsonWorldDataService;  // NEW

    private final WorldData worldData = new WorldData();
    @Value("${world.defaultName:defaultWorld}")
    private String defaultWorldName;
    @Value("${world.saveDir:save/worlds/}")
    private String saveDir;
    private boolean initialized = false;

    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;

    private boolean isMultiplayerMode = false;
    @Autowired
    private ChunkLoadingQueue chunkLoadingQueue;
    private boolean disconnectHandled = false;


    public ClientWorldServiceImpl(
        WorldConfig worldConfig,
        WorldGenerator worldGenerator,
        WorldObjectManager worldObjectManager,
        TileManager tileManager,
        BiomeConfigurationLoader biomeLoader,
        BiomeService biomeService,
        ObjectTextureManager objectTextureManager,
        @Qualifier("clientJsonWorldDataService") JsonWorldDataService jsonWorldDataService
    ) {
        this.worldGenerator = worldGenerator;
        this.worldObjectManager = worldObjectManager;
        this.tileManager = tileManager;
        this.biomeLoader = biomeLoader;
        this.biomeService = biomeService;
        // this.worldMetadataRepo = worldMetadataRepo;   // REMOVED
        // this.chunkRepository = chunkRepository;       // REMOVED
        // this.playerDataRepository = playerDataRepository; // REMOVED
        this.objectTextureManager = objectTextureManager;
        this.jsonWorldDataService = jsonWorldDataService; // NEW
    }

    @Override
    public void generateWorldThumbnail(String worldName) {
        initIfNeeded();
        objectTextureManager.initializeIfNeeded();
        int previewSize = 8;
        int tileSize = 32;
        int iconWidth = 128;
        int iconHeight = 128;

        float scaleX = (float) iconWidth / (previewSize * tileSize);
        float scaleY = (float) iconHeight / (previewSize * tileSize);
        float scale = Math.min(scaleX, scaleY);

        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, iconWidth, iconHeight, false);
        SpriteBatch batch = new SpriteBatch();

        fbo.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        OrthographicCamera camera = new OrthographicCamera(iconWidth, iconHeight);
        camera.setToOrtho(true, iconWidth, iconHeight);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        com.badlogic.gdx.math.Matrix4 transform = batch.getTransformMatrix();
        transform.idt();

        float worldWidth = previewSize * tileSize;
        float worldHeight = previewSize * tileSize;
        transform.translate(iconWidth / 2f, iconHeight / 2f, 0);
        transform.scale(scale, scale, 1f);
        transform.translate(-worldWidth / 2f, -worldHeight / 2f, 0);

        batch.setTransformMatrix(transform);

        int centerX = 0;
        int centerY = 0;


        for (int dy = 0; dy < previewSize; dy++) {
            for (int dx = 0; dx < previewSize; dx++) {
                int tileX = centerX + dx - previewSize / 2;
                int tileY = centerY + dy - previewSize / 2;
                int chunkX = tileX / 16;
                int chunkY = tileY / 16;
                int[][] tiles = getChunkTiles(chunkX, chunkY);
                if (tiles != null) {
                    int localX = Math.floorMod(tileX, 16);
                    int localY = Math.floorMod(tileY, 16);
                    if (localX >= 0 && localX < 16 && localY >= 0 && localY < 16) {
                        int tileType = tiles[localX][localY];
                        TextureRegion region = tileManager.getRegionForTile(tileType);
                        if (region != null) {
                            float worldPixelX = dx * tileSize;
                            float worldPixelY = dy * tileSize;
                            batch.draw(region, worldPixelX, worldPixelY, tileSize, tileSize);
                        }
                    }
                }
            }
        }


        Set<String> processedChunks = new HashSet<>();
        for (int dy = 0; dy < previewSize; dy++) {
            for (int dx = 0; dx < previewSize; dx++) {
                int tileX = centerX + dx - previewSize / 2;
                int tileY = centerY + dy - previewSize / 2;
                int chunkX = tileX / 16;
                int chunkY = tileY / 16;
                String key = chunkX + "," + chunkY;
                if (!processedChunks.contains(key)) {
                    List<WorldObject> objs = worldObjectManager.getObjectsForChunk(chunkX, chunkY);
                    for (WorldObject obj : objs) {
                        int objTileX = obj.getTileX();
                        int objTileY = obj.getTileY();
                        if (objTileX >= centerX - previewSize / 2 && objTileX < centerX + previewSize / 2 &&
                            objTileY >= centerY - previewSize / 2 && objTileY < centerY + previewSize / 2) {
                            float worldPixelX = (objTileX - (centerX - (float) previewSize / 2)) * tileSize;
                            float worldPixelY = (objTileY - (centerY - (float) previewSize / 2)) * tileSize;


                            TextureRegion objTexture = objectTextureManager.getTexture(obj.getType().getTextureRegionName());
                            if (objTexture != null) {
                                batch.draw(objTexture, worldPixelX, worldPixelY,
                                    obj.getType().getWidthInTiles() * tileSize,
                                    obj.getType().getHeightInTiles() * tileSize);
                            }
                        }
                    }
                    processedChunks.add(key);
                }
            }
        }

        batch.end();

        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, iconWidth, iconHeight);
        fbo.end();


        FileHandle dir = Gdx.files.local(saveDir + worldName);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        FileHandle iconFile = Gdx.files.local(saveDir + worldName + "/icon.png");
        PixmapIO.writePNG(iconFile, pm);

        pm.dispose();
        batch.dispose();
        fbo.dispose();

        log.info("Generated world thumbnail for '{}'", worldName);
    }

    @Override
    public int[][] getChunkTiles(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        ChunkData cData = getWorldData().getChunks().get(key);

        if (cData == null) {
            if (isMultiplayerMode) {
                if (!chunkLoadingQueue.isChunkInProgress(chunkX, chunkY)) {
                    log.debug("Requesting missing chunk {}", key);
                    chunkLoadingQueue.queueChunkRequest(chunkX, chunkY);
                    multiplayerClient.requestChunk(chunkX, chunkY);
                }
                return null;
            } else {
                loadOrGenerateChunk(chunkX, chunkY);
                cData = getWorldData().getChunks().get(key);
            }
        }

        return cData != null ? cData.getTiles() : null;
    }

    @Override
    public void loadOrReplaceChunkData(int chunkX, int chunkY, int[][] tiles, List<WorldObject> objects) {
        // First ensure we have a world initialized
        if (worldData.getWorldName() == null) {
            worldData.setWorldName("serverWorld");
            worldData.setSeed(System.currentTimeMillis());
            log.info("Auto-initialized multiplayer world during chunk load");
        }

        String key = chunkX + "," + chunkY;
        ChunkData cData = new ChunkData();
        cData.setChunkX(chunkX);
        cData.setChunkY(chunkY);
        cData.setTiles(tiles);
        cData.setObjects(objects != null ? new ArrayList<>(objects) : new ArrayList<>());

        worldData.getChunks().put(key, cData);
        log.debug("Updated chunk {}: tiles={}, objects={}",
            key, tiles != null, objects != null ? objects.size() : 0);
    }
    @Override
    public void updateWorldObjectState(WorldObjectUpdate update) {
        String key = (update.getTileX() / 16) + "," + (update.getTileY() / 16);
        ChunkData chunk = getWorldData().getChunks().get(key);
        if (chunk == null) return; // chunk not loaded

        List<WorldObject> objs = chunk.getObjects();
        if (update.isRemoved()) {
            objs.removeIf(o -> o.getId().equals(update.getObjectId()));
        } else {
            boolean found = false;
            for (WorldObject wo : objs) {
                if (wo.getId().equals(update.getObjectId())) {
                    wo.setTileX(update.getTileX());
                    wo.setTileY(update.getTileY());
                    found = true;
                    break;
                }
            }
            if (!found) {
                ObjectType objType = ObjectType.valueOf(update.getType());
                WorldObject newObj = new WorldObject(
                    update.getTileX(),
                    update.getTileY(),
                    objType,
                    objType.isCollidable()
                );
                objs.add(newObj);
            }
        }

        // Save chunk
        try {
            jsonWorldDataService.saveChunk(getWorldData().getWorldName(), chunk);
        } catch (IOException e) {
            log.error("Failed to save chunk after updateWorldObjectState: {}", e.getMessage());
        }
    }

    @Override
    public TileManager getTileManager() {
        return this.tileManager;
    }


    @Override
    public void loadWorld(String worldName) {
        // Reset disconnect handling flag
        disconnectHandled = false;

        // Clear any existing world state first
        clearWorldData();

        // Explicitly set to singleplayer mode
        setMultiplayerMode(false);

        try {
            jsonWorldDataService.loadWorld(worldName, worldData);
            worldData.setLastPlayed(System.currentTimeMillis());
            initIfNeeded();
            log.info("Loaded singleplayer world data for world: {}", worldName);
        } catch (IOException e) {
            log.error("Failed to load world '{}': {}", worldName, e.getMessage());
            throw new RuntimeException("Failed to load world: " + worldName, e);
        }
    }


    public void handleDisconnect() {
        if (!disconnectHandled && isMultiplayerMode()) {
            disconnectHandled = true;
            log.info("Handling disconnection cleanup...");

            // Save any necessary data before clearing
            saveWorldData();

            // Clear world data
            clearWorldData();

            // Reset multiplayer mode
            setMultiplayerMode(false);

            log.info("Disconnection cleanup completed");
        }
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

        // Clear any chunk requests
        if (multiplayerClient != null) {
            multiplayerClient.clearPendingChunkRequests();
        }
    }


    @Override
    public void initIfNeeded() {
        if (initialized) {
            return;
        }

        Map<BiomeType, Biome> biomes = biomeLoader.loadBiomes("config/biomes.json");
        if (worldData.getSeed() == 0) {
            long randomSeed = new Random().nextLong();
            worldData.setSeed(randomSeed);
            log.info("No existing seed found; using random seed: {}", randomSeed);
        }

        long seed = worldData.getSeed();
        worldGenerator.setSeedAndBiomes(seed, biomes);
        biomeService.initWithSeed(seed);
        worldObjectManager.initialize();
        tileManager.initIfNeeded();

        initialized = true;
        log.info("WorldService initialized with seed {}", seed);
    }

    @Override
    public void saveWorldData() {
        if (worldData.getWorldName() == null || worldData.getWorldName().isEmpty()) {
            log.debug("No world loaded, nothing to save.");
            return;
        }

        try {
            // Update timestamps
            long now = System.currentTimeMillis();
            long playTime = now - worldData.getLastPlayed();
            worldData.setPlayedTime(worldData.getPlayedTime() + playTime);
            worldData.setLastPlayed(now);

            jsonWorldDataService.saveWorld(worldData);
            log.info("Saved world data for '{}' (played time: {}ms)",
                worldData.getWorldName(), worldData.getPlayedTime());
        } catch (IOException e) {
            log.error("Failed saving world '{}': {}", worldData.getWorldName(), e.getMessage());
        }
    }

    @Override
    public boolean createWorld(String worldName, long seed) {
        // Check if world exists first
        if (jsonWorldDataService.worldExists(worldName)) {
            log.warn("World '{}' already exists, cannot create", worldName);
            return false;
        }

        // Clear any existing world data first
        worldData.getChunks().clear();
        worldData.getPlayers().clear();

        // Set up new world
        long now = System.currentTimeMillis();
        worldData.setWorldName(worldName);
        worldData.setSeed(seed);
        worldData.setCreatedDate(now);
        worldData.setLastPlayed(now);
        worldData.setPlayedTime(0);

        try {
            jsonWorldDataService.saveWorld(worldData);
            log.info("Created new world '{}' with seed {}", worldName, seed);
            return true;
        } catch (IOException e) {
            log.error("Failed to create world '{}': {}", worldName, e.getMessage());
            return false;
        }
    }

    @Override
    public WorldData getWorldData() {
        return worldData;
    }

    @Override
    public void loadWorldData() {
        try {
            jsonWorldDataService.loadWorld(defaultWorldName, worldData);
            initIfNeeded();
            log.info("Loaded default world data for '{}' from JSON", defaultWorldName);
        } catch (IOException e) {
            log.warn("No default world '{}' found in JSON: {}", defaultWorldName, e.getMessage());
        }
    }


    @Override
    public Map<String, ChunkData> getVisibleChunks(Rectangle viewBounds) {
        // Don't check for worldName anymore since we auto-initialize
        Map<String, ChunkData> visibleChunks = new HashMap<>();
        int startChunkX = (int) Math.floor(viewBounds.x / (CHUNK_SIZE * TILE_SIZE));
        int startChunkY = (int) Math.floor(viewBounds.y / (CHUNK_SIZE * TILE_SIZE));
        int endChunkX = (int) Math.ceil((viewBounds.x + viewBounds.width) / (CHUNK_SIZE * TILE_SIZE));
        int endChunkY = (int) Math.ceil((viewBounds.y + viewBounds.height) / (CHUNK_SIZE * TILE_SIZE));

        log.debug("Getting chunks in range: ({},{}) to ({},{})",
            startChunkX, startChunkY, endChunkX, endChunkY);

        for (int x = startChunkX; x <= endChunkX; x++) {
            for (int y = startChunkY; y <= endChunkY; y++) {
                String key = x + "," + y;
                ChunkData chunk = worldData.getChunks().get(key);
                if (chunk == null) {
                    if (isMultiplayerMode && multiplayerClient.isConnected()) {
                        multiplayerClient.requestChunk(x, y);
                    }
                } else {
                    visibleChunks.put(key, chunk);
                }
            }
        }

        return visibleChunks;
    }

    private void loadOrGenerateChunk(int chunkX, int chunkY) {
        if (isMultiplayerMode) {
            return;
        }
        // 1) Attempt load from JSON
        try {
            ChunkData loaded = jsonWorldDataService.loadChunk(worldData.getWorldName(), chunkX, chunkY);
            if (loaded != null) {
                worldObjectManager.loadObjectsForChunk(chunkX, chunkY, loaded.getObjects());
                worldData.getChunks().put(chunkX + "," + chunkY, loaded);
                return;
            }
        } catch (IOException e) {
            log.warn("Failed reading chunk from JSON: {}", e.getMessage());
        }

        // 2) Generate
        int[][] tiles = worldGenerator.generateChunk(chunkX, chunkY);
        ChunkData cData = new ChunkData();
        cData.setChunkX(chunkX);
        cData.setChunkY(chunkY);

        cData.setTiles(tiles);
        Biome biome = worldGenerator.getBiomeForChunk(chunkX, chunkY);
        List<WorldObject> objs = worldObjectManager.generateObjectsForChunk(
            chunkX, chunkY, tiles, biome, getWorldData().getSeed());
        cData.setObjects(objs);
        worldData.getChunks().put(chunkX + "," + chunkY, cData);

        // 3) Save
        try {
            jsonWorldDataService.saveChunk(worldData.getWorldName(), cData);
        } catch (IOException e) {
            log.error("Failed to save chunk for newly generated chunk: {}", e.getMessage());
        }
    }

    @Override
    public boolean isChunkLoaded(Vector2 chunkPos) {
        String key = String.format("%d,%d", (int) chunkPos.x, (int) chunkPos.y);
        boolean loaded = worldData.getChunks().containsKey(key);
        if (!loaded) {
            log.debug("Chunk {} not loaded, current chunks: {}",
                key, worldData.getChunks().keySet());
        }
        return loaded;
    }

    @Override
    public void loadChunk(Vector2 chunkPos) {
        // Only attempt to load/generate if we have a valid world
        if (worldData.getWorldName() == null) {
            log.debug("Cannot load chunk - no world loaded");
            return;
        }

        String key = String.format("%d,%d", (int)chunkPos.x, (int)chunkPos.y);
        if (multiplayerClient != null && multiplayerClient.isPendingChunkRequest((int)chunkPos.x, (int)chunkPos.y)) {
            log.debug("Chunk {} already requested, skipping", key);
            return;
        }

        if (isMultiplayerMode()) {
            log.debug("Requesting chunk {} from server", key);
            multiplayerClient.requestChunk((int)chunkPos.x, (int)chunkPos.y);
        } else {
            loadOrGenerateChunk((int)chunkPos.x, (int)chunkPos.y);
        }
    }

    @Override
    public List<WorldObject> getVisibleObjects(Rectangle viewBounds) {
        if (worldData.getWorldName() == null || worldData.getWorldName().isEmpty()) {
            return Collections.emptyList();
        }
        List<WorldObject> visibleObjects = new ArrayList<>();
        Map<String, ChunkData> visibleChunks = getVisibleChunks(viewBounds);
        for (ChunkData chunk : visibleChunks.values()) {
            if (chunk.getObjects() != null) {
                for (WorldObject obj : chunk.getObjects()) {
                    float pixelX = obj.getTileX() * TILE_SIZE;
                    float pixelY = obj.getTileY() * TILE_SIZE;
                    if (viewBounds.contains(pixelX, pixelY)) {
                        visibleObjects.add(obj);
                    }
                }
            }
        }
        return visibleObjects;
    }

    @Override
    public void setPlayerData(PlayerData playerData) {
        if (playerData == null) {
            log.warn("Attempt to set null player data");
            return;
        }

        // Always update in-memory state
        worldData.getPlayers().put(playerData.getUsername(), playerData);

        // Only save to disk in singleplayer mode
        if (!isMultiplayerMode) {
            try {
                jsonWorldDataService.savePlayerData(worldData.getWorldName(), playerData);
                log.debug("Saved player data for {} in singleplayer mode", playerData.getUsername());
            } catch (IOException e) {
                log.error("Failed to save player data: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isMultiplayerMode() {
        return this.isMultiplayerMode;
    }



    @Override
    public void setMultiplayerMode(boolean multiplayer) {
        if (this.isMultiplayerMode() != multiplayer) {
            this.isMultiplayerMode = multiplayer;

            if (multiplayer) {
                // Initialize a default world for multiplayer
                worldData.setWorldName("serverWorld");
                worldData.setSeed(System.currentTimeMillis()); // Server will override this
                log.info("Initialized multiplayer world");
            } else {
                // Clear all multiplayer state
                clearWorldData();
                if (multiplayerClient != null) {
                    multiplayerClient.clearPendingChunkRequests();
                }
            }
        }
    }


    @Override
    public PlayerData getPlayerData(String username) {
        PlayerData pd = getWorldData().getPlayers().get(username);
        if (pd == null) {
            String wName = getWorldData().getWorldName();
            try {
                pd = jsonWorldDataService.loadPlayerData(wName, username);
                if (pd == null) {
                    pd = new PlayerData(username, 0, 0, PlayerDirection.DOWN);
                    jsonWorldDataService.savePlayerData(wName, pd);
                }
                getWorldData().getPlayers().put(username, pd);
            } catch (IOException e) {
                log.error("Failed to load or create player data for {}: {}", username, e.getMessage());
            }
        }
        return pd;
    }


    @Override
    public List<String> getAvailableWorlds() {
        return jsonWorldDataService.listAllWorlds();
    }

    @Override
    public void deleteWorld(String worldName) {
        if (!jsonWorldDataService.worldExists(worldName)) {
            log.warn("World '{}' does not exist, cannot delete", worldName);
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
            log.info("Cleared current loaded world data because it was deleted.");
        }
        log.info("Deleted world '{}'", worldName);
    }

    @Override
    public void regenerateChunk(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        worldData.getChunks().remove(key);
        jsonWorldDataService.deleteChunk(worldData.getWorldName(), chunkX, chunkY);
        loadOrGenerateChunk(chunkX, chunkY);
    }
}
