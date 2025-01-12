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
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import io.github.minemon.world.biome.model.Biome;
import io.github.minemon.world.biome.model.BiomeType;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.config.WorldConfig;
import io.github.minemon.world.model.*;
import io.github.minemon.world.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClientWorldServiceImpl extends BaseWorldServiceImpl implements WorldService {
    private static final int CHUNK_SIZE = 16;
    private static final int TILE_SIZE = 32;
    private static final long CHUNK_REQUEST_TIMEOUT = 2000;
    private static final long URGENT_REQUEST_TIMEOUT = 1000;
    private final WorldGenerator worldGenerator;
    @Autowired
    @Lazy
    private final WorldObjectManager worldObjectManager;
    private final TileManager tileManager;
    private final ObjectTextureManager objectTextureManager;
    private final BiomeConfigurationLoader biomeLoader;
    private final BiomeService biomeService;
    private final JsonWorldDataService jsonWorldDataService;  
    private final WorldData worldData = new WorldData();
    private final Map<String, Long> chunkRequestTimes = new ConcurrentHashMap<>();
    private final Set<Vector2> failedRequests = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Object> chunkLocks = new ConcurrentHashMap<>();
    @Value("${world.defaultName:defaultWorld}")
    private String defaultWorldName;
    @Value("${world.saveDir:save/worlds/}")
    private String saveDir;
    
    private String getActualSaveDir() {
        try {
            // First ensure the base directories exist
            fileAccessService.ensureDirectoryExists("save");
            fileAccessService.ensureDirectoryExists("save/worlds");
            
            // Get the full path for logging
            String worldsPath = fileAccessService.getBasePath() + "/save/worlds";
            log.info("Using worlds directory: {}", worldsPath);
            
            // Return the relative path since FileAccessService handles the base path
            return "save/worlds/";
        } catch (Exception e) {
            String msg = "Failed to initialize worlds directory: " + e.getMessage();
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }
    private boolean initialized = false;
    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;
    private boolean isMultiplayerMode = false;
    private boolean disconnectHandled = false;
    @Autowired
    @Lazy
    private ChunkLoadingManager chunkLoadingManager;
    @Autowired
    @Lazy
    private PlayerService playerService;

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
        this.objectTextureManager = objectTextureManager;
        this.jsonWorldDataService = jsonWorldDataService;
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


        FileHandle dir = Gdx.files.local(getActualSaveDir() + worldName);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        FileHandle iconFile = Gdx.files.local(getActualSaveDir() + worldName + "/icon.png");
        PixmapIO.writePNG(iconFile, pm);

        pm.dispose();
        batch.dispose();
        fbo.dispose();

        log.info("Generated world thumbnail for '{}'", worldName);
    }

    @Override
    public void updateWorldObjectState(WorldObjectUpdate update) {
        String key = (update.getTileX() / 16) + "," + (update.getTileY() / 16);
        ChunkData chunk = getWorldData().getChunks().get(key);
        if (chunk == null) return; 

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
        
        disconnectHandled = false;

        
        clearWorldData();

        
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
        if (!isMultiplayerMode()) {
            return;
        }
        if (!disconnectHandled && isMultiplayerMode()) {
            disconnectHandled = true;
            log.info("Handling disconnection cleanup...");

            
            saveWorldData();

            
            clearWorldData();

            
            setMultiplayerMode(false);
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

    public void preloadChunksAroundPosition(float tileX, float tileY) {
        int IMMEDIATE_RADIUS = 2;  
        int PRELOAD_RADIUS = 4;    

        int centerChunkX = (int) Math.floor(tileX / CHUNK_SIZE);
        int centerChunkY = (int) Math.floor(tileY / CHUNK_SIZE);

        
        if (multiplayerClient != null) {
            multiplayerClient.clearPendingChunkRequests();
        }

        
        for (int dx = -IMMEDIATE_RADIUS; dx <= IMMEDIATE_RADIUS; dx++) {
            for (int dy = -IMMEDIATE_RADIUS; dy <= IMMEDIATE_RADIUS; dy++) {
                int chunkX = centerChunkX + dx;
                int chunkY = centerChunkY + dy;

                if (!isChunkLoaded(new Vector2(chunkX, chunkY))) {
                    if (isMultiplayerMode && multiplayerClient != null) {
                        chunkLoadingManager.queueChunkRequest(chunkX, chunkY, true); 
                    } else {
                        loadOrGenerateChunk(chunkX, chunkY);
                    }
                }
            }
        }

        
        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dy = -PRELOAD_RADIUS; dy <= PRELOAD_RADIUS; dy++) {
                
                if (Math.abs(dx) <= IMMEDIATE_RADIUS && Math.abs(dy) <= IMMEDIATE_RADIUS) {
                    continue;
                }

                int chunkX = centerChunkX + dx;
                int chunkY = centerChunkY + dy;

                if (!isChunkLoaded(new Vector2(chunkX, chunkY))) {
                    if (isMultiplayerMode && multiplayerClient != null) {
                        chunkLoadingManager.queueChunkRequest(chunkX, chunkY, false); 
                    } else {
                        loadOrGenerateChunk(chunkX, chunkY);
                    }
                }
            }
        }
    }

    @Override
    public void saveWorldData() {
        
        if (isMultiplayerMode) {
            log.debug("Skipping world save in multiplayer mode");
            return;
        }

        if (worldData.getWorldName() == null || worldData.getWorldName().isEmpty()) {
            log.debug("No world loaded, nothing to save.");
            return;
        }

        try {
            
            jsonWorldDataService.saveWorld(worldData);
            log.info("Saved world data for '{}'", worldData.getWorldName());
        } catch (IOException e) {
            log.error("Failed saving world '{}': {}", worldData.getWorldName(), e.getMessage());
        }
    }

    @Override
    public boolean createWorld(String worldName, long seed) {
        
        if (jsonWorldDataService.worldExists(worldName)) {
            log.warn("World '{}' already exists, cannot create", worldName);
            return false;
        }

        
        worldData.getChunks().clear();
        worldData.getPlayers().clear();

        
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
            log.error("Failed to create world '{}': {} (saveDir={})", worldName, e.getMessage(), getActualSaveDir());
            return false;
        }
    }

    @Override
    public WorldData getWorldData() {
        return worldData;
    }

    private boolean isChunkUrgent(int chunkX, int chunkY, Rectangle viewBounds) {
        float chunkWorldX = chunkX * CHUNK_SIZE * TILE_SIZE;
        float chunkWorldY = chunkY * CHUNK_SIZE * TILE_SIZE;
        Rectangle chunkBounds = new Rectangle(
            chunkWorldX,
            chunkWorldY,
            CHUNK_SIZE * TILE_SIZE,
            CHUNK_SIZE * TILE_SIZE
        );
        return viewBounds.overlaps(chunkBounds);
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
        Map<String, ChunkData> visibleChunks = new HashMap<>();

        
        int startChunkX = (int) Math.floor((viewBounds.x - TILE_SIZE) / (CHUNK_SIZE * TILE_SIZE));
        int startChunkY = (int) Math.floor((viewBounds.y - TILE_SIZE) / (CHUNK_SIZE * TILE_SIZE));
        int endChunkX = (int) Math.ceil((viewBounds.x + viewBounds.width + TILE_SIZE) / (CHUNK_SIZE * TILE_SIZE));
        int endChunkY = (int) Math.ceil((viewBounds.y + viewBounds.height + TILE_SIZE) / (CHUNK_SIZE * TILE_SIZE));

        
        for (int x = startChunkX; x <= endChunkX; x++) {
            for (int y = startChunkY; y <= endChunkY; y++) {
                String key = x + "," + y;
                ChunkData chunk = worldData.getChunks().get(key);

                if (chunk != null) {
                    visibleChunks.put(key, chunk);
                } else if (isMultiplayerMode && !chunkLoadingManager.isChunkInProgress(x, y)) {
                    
                    chunkLoadingManager.queueChunkRequest(x, y, true); 
                }
            }
        }

        return visibleChunks;
    }

    private void unloadDistantChunks(Rectangle viewBounds) {
        if (isMultiplayerMode) {
            return; 
        }
        final int UNLOAD_DISTANCE = 5; 

        int playerChunkX = (int) Math.floor(viewBounds.x / (CHUNK_SIZE * TILE_SIZE));
        int playerChunkY = (int) Math.floor(viewBounds.y / (CHUNK_SIZE * TILE_SIZE));

        
        Set<String> keys = new HashSet<>(worldData.getChunks().keySet());

        for (String key : keys) {
            ChunkData chunk = worldData.getChunks().get(key);
            if (chunk == null) continue;

            int dx = Math.abs(chunk.getChunkX() - playerChunkX);
            int dy = Math.abs(chunk.getChunkY() - playerChunkY);

            if (dx > UNLOAD_DISTANCE || dy > UNLOAD_DISTANCE) {
                worldData.getChunks().remove(key);
            }
        }
    }

    public void update(float delta) {
        try {
            if (camera != null) {
                Rectangle viewBounds = calculateViewBounds();
                unloadDistantChunks(viewBounds);
            }
        } catch (Exception e) {
            log.error("Error during world update: {}", e.getMessage(), e);
        }
    }

    private Rectangle calculateViewBounds() {
        if (camera == null) {
            return new Rectangle(0, 0, CHUNK_SIZE * TILE_SIZE, CHUNK_SIZE * TILE_SIZE);
        }

        
        float width = camera.viewportWidth * camera.zoom;
        float height = camera.viewportHeight * camera.zoom;

        
        float bufferSize = 2 * CHUNK_SIZE * TILE_SIZE;

        return new Rectangle(
            camera.position.x - (width / 2) - bufferSize,
            camera.position.y - (height / 2) - bufferSize,
            width + (bufferSize * 2),
            height + (bufferSize * 2)
        );
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
                        
                        chunkLoadingManager.queueChunkRequest(cx, cy, true);
                    } else {
                        loadOrGenerateChunk(cx, cy);
                    }
                }
            }
        }
    }

    @Override
    public int[][] getChunkTiles(int chunkX, int chunkY) {
        String key = chunkX + "," + chunkY;
        ChunkData chunkData = worldData.getChunks().get(key);

        if (chunkData == null && isMultiplayerMode()) {
            
            Vector2 playerChunkPos = new Vector2(
                playerService.getPlayerData().getX() / CHUNK_SIZE,
                playerService.getPlayerData().getY() / CHUNK_SIZE
            );

            Vector2 requestedChunkPos = new Vector2(chunkX, chunkY);
            float distance = playerChunkPos.dst(requestedChunkPos);

            
            boolean urgent = distance <= 3;

            if (!chunkLoadingManager.isChunkInProgress(chunkX, chunkY)) {
                
                chunkLoadingManager.queueChunkRequest(chunkX, chunkY, urgent);
                log.debug("Queued {} priority chunk request for ({},{})",
                    urgent ? "high" : "normal", chunkX, chunkY);
            }
            return null; 
        }

        if (chunkData != null) {
            
            return chunkData.getTiles();
        }

        return null;
    }
    @Override
    public void loadOrReplaceChunkData(int chunkX, int chunkY, int[][] tiles, List<WorldObject> objects) {
        
        String key = chunkX + "," + chunkY;

        synchronized (chunkLocks.computeIfAbsent(key, k -> new Object())) {
            ChunkData chunk = worldData.getChunks().computeIfAbsent(key, k -> {
                ChunkData newChunk = new ChunkData();
                newChunk.setChunkX(chunkX);
                newChunk.setChunkY(chunkY);
                return newChunk;
            });

            
            if (tiles != null) {
                chunk.setTiles(tiles);
            }

            
            if (objects != null) {
                if (chunk.getObjects() == null) {
                    chunk.setObjects(new ArrayList<>(objects));
                } else {
                    
                    List<WorldObject> mergedObjects = new ArrayList<>(
                        chunk.getObjects().stream()
                            .filter(existing -> objects.stream()
                                .noneMatch(newObj ->
                                    newObj.getId().equals(existing.getId())))
                            .collect(Collectors.toList())
                    );
                    mergedObjects.addAll(objects);
                    chunk.setObjects(mergedObjects);
                }
            }
            chunkLoadingManager.markChunkComplete(chunkX, chunkY);
        }
    }

    @Override
    public ChunkData loadOrGenerateChunk(int chunkX, int chunkY) {
        if (isMultiplayerMode) {
            return null;
        }
        
        try {
            ChunkData loaded = jsonWorldDataService.loadChunk(worldData.getWorldName(), chunkX, chunkY);
            if (loaded != null) {
                worldObjectManager.loadObjectsForChunk(chunkX, chunkY, loaded.getObjects());
                worldData.getChunks().put(chunkX + "," + chunkY, loaded);
                return null;
            }
        } catch (IOException e) {
            log.warn("Failed reading chunk from JSON: {}", e.getMessage());
        }

        
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

        
        try {
            jsonWorldDataService.saveChunk(worldData.getWorldName(), cData);
        } catch (IOException e) {
            log.error("Failed to save chunk for newly generated chunk: {}", e.getMessage());
        }
        return cData;
    }

    @Override
    public synchronized boolean isChunkLoaded(Vector2 chunkPos) {
        String key = String.format("%d,%d", (int) chunkPos.x, (int) chunkPos.y);
        Map<String, ChunkData> chunks = worldData.getChunks();
        boolean loaded = chunks.containsKey(key);
        return loaded;
    }

    private void requestChunkWithTimeout(int chunkX, int chunkY, boolean urgent) {
        String key = chunkX + "," + chunkY;
        long now = System.currentTimeMillis();
        Long lastRequest = chunkRequestTimes.get(key);
        long timeout = urgent ? URGENT_REQUEST_TIMEOUT : CHUNK_REQUEST_TIMEOUT;

        
        if (lastRequest != null && now - lastRequest < timeout) {
            return; 
        }

        if (multiplayerClient != null && multiplayerClient.isConnected()) {
            if (!multiplayerClient.isPendingChunkRequest(chunkX, chunkY)) {
                if (isMultiplayerMode && !chunkLoadingManager.isChunkInProgress(chunkX, chunkY)) {
                    chunkLoadingManager.queueChunkRequest(chunkX, chunkY,  true);
                }

                chunkRequestTimes.put(key, now);

                if (urgent) {
                    
                    failedRequests.add(new Vector2(chunkX, chunkY));
                }
            }
        }
    }


    @Override
    public void loadChunk(Vector2 chunkPos) {
        if (isMultiplayerMode) {
            chunkLoadingManager.queueChunkRequest((int) chunkPos.x, (int) chunkPos.y, false);
        } else {
            loadOrGenerateChunk((int) chunkPos.x, (int) chunkPos.y);
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

        
        worldData.getPlayers().put(playerData.getUsername(), playerData);

        
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
                
                worldData.setWorldName("serverWorld");
                worldData.setSeed(System.currentTimeMillis()); 
                log.info("Initialized multiplayer world");
            } else {
                
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
