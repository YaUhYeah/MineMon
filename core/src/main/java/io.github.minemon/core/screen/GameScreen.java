package io.github.minemon.core.screen;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.chat.ui.ChatTable;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.ui.HotbarUI;
import io.github.minemon.input.InputService;
import io.github.minemon.inventory.service.impl.ItemTextureManager;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.multiplayer.service.impl.ClientConnectionManager;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.player.model.RemotePlayerAnimator;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.model.WorldRenderer;
import io.github.minemon.world.service.ChunkLoaderService;
import io.github.minemon.world.service.ChunkLoadingManager;
import io.github.minemon.world.service.ChunkPreloaderService;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Component
@Scope("prototype")
@Slf4j
public class GameScreen implements Screen {
    private static final long CHUNK_UPDATE_INTERVAL = 250; 
    private final float TARGET_VIEWPORT_WIDTH_TILES = 24f;
    private final int TILE_SIZE = 32;
    private final PlayerService playerService;
    private final WorldService worldService;
    private final AudioService audioService;
    private final InputService inputService;
    private final ChatService chatService;
    private final BiomeService biomeService;
    private final WorldRenderer worldRenderer;
    private final ChunkLoaderService chunkLoaderService;
    private final ScreenManager screenManager;
    private final MultiplayerClient multiplayerClient;
    private final PlayerAnimationService animationService;
    private final Map<String, RemotePlayerAnimator> remotePlayerAnimators = new ConcurrentHashMap<>();
    @Autowired
    private InventoryScreen inventoryScreen;
    private boolean handlingDisconnect = false;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    @Autowired
    @Lazy
    private ClientConnectionManager connectionManager;
    private BitmapFont font;
    private Stage pauseStage;
    private Skin pauseSkin;
    private Stage hudStage;
    private Skin hudSkin;
    private ChatTable chatTable;
    private boolean showDebug = false;
    private boolean paused = false;
    private float cameraPosX, cameraPosY;
    private Image pauseOverlay;
    private InputMultiplexer multiplexer;
    private boolean isActuallyMultiplayer = false;
    @Autowired
    @Lazy
    private ItemTextureManager itemTextureManager;
    private final ChunkLoadingManager chunkLoadingManager;
    private long lastChunkUpdate = 0;
    @Autowired
    @Lazy
    private HotbarUI hotbarUI;

    @Autowired
    public GameScreen(PlayerService playerService,
                      WorldService worldService,
                      AudioService audioService,
                      InputService inputService,
                      ScreenManager screenManager,
                      ChatService chatService,
                      BiomeService biomeService,
                      WorldRenderer worldRenderer,
                      ChunkLoaderService chunkLoaderService,
                      ChunkPreloaderService chunkPreloaderService, PlayerAnimationService animationService, MultiplayerClient client,
                      ChunkLoadingManager chunkLoadingManager) {
        this.playerService = playerService;
        this.worldService = worldService;
        this.audioService = audioService;
        this.inputService = inputService;
        this.animationService = animationService;
        this.chatService = chatService;
        this.screenManager = screenManager;
        this.biomeService = biomeService;
        this.worldRenderer = worldRenderer;
        this.chunkLoaderService = chunkLoaderService;
        this.multiplayerClient = client;
        this.chunkLoadingManager = chunkLoadingManager;
    }

    private void handleDisconnection() {
        
        if (!isActuallyMultiplayer) {
            return;
        }

        
        if (!handlingDisconnect && multiplayerClient != null
            && !multiplayerClient.isConnected()
            && worldService.isMultiplayerMode()) {

            handlingDisconnect = true;
            worldService.saveWorldData();
            worldService.handleDisconnect();

            ServerDisconnectScreen disconnectScreen = screenManager.getScreen(ServerDisconnectScreen.class);
            disconnectScreen.setDisconnectReason("TIMEOUT");
            screenManager.showScreen(ServerDisconnectScreen.class);
        }
    }
    private boolean worldRendererIsInitialized() {
        return worldRenderer != null && worldRenderer.isInitialized();
    }
    @Override
    public void show() {
        try {
            if (!worldRendererIsInitialized()) {
                worldRenderer.initialize();
            }

            // Ensure HotbarUI is initialized
            if (hotbarUI == null) {
                log.error("HotbarUI is null - attempting to get from context");
                hotbarUI = GameApplicationContext.getBean(HotbarUI.class);
            }
            hotbarUI.initialize();

            // Ensure WorldService is initialized
            if (worldService == null) {
                log.error("WorldService is null - attempting to get from context");
                worldService = GameApplicationContext.getBean(WorldService.class);
            }

            handlingDisconnect = false;
            isActuallyMultiplayer = worldService.isMultiplayerMode();

        log.debug("GameScreen.show() >> current worldName={}, seed={}",
            worldService.getWorldData().getWorldName(),
            worldService.getWorldData().getSeed());

        inventoryScreen.init();
        itemTextureManager.initialize(new TextureAtlas(Gdx.files.internal("atlas/items-gfx-atlas.atlas")));

        
        if (worldService.isMultiplayerMode() &&
            worldService.getWorldData().getWorldName() == null) {
            worldService.getWorldData().setWorldName("serverWorld");
            worldService.getWorldData().setSeed(System.currentTimeMillis());
            log.info("Initialized multiplayer world in GameScreen");
        }

        
        PlayerData pd = playerService.getPlayerData().getUsername() != null
            ? playerService.getPlayerData()
            : null;
        if (pd != null) {
            log.debug("Player data: username={}, x={}, y={}",
                pd.getUsername(), pd.getX(), pd.getY());
        }

        animationService.initAnimationsIfNeeded();

        float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
        float aspect = (float) Gdx.graphics.getHeight() / (float) Gdx.graphics.getWidth();
        float baseHeight = baseWidth * aspect;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, baseWidth, baseHeight);
        worldService.setCamera(camera);

        batch = new SpriteBatch();
        font = new BitmapFont();
        initializeUI();

        
        multiplexer = new InputMultiplexer();

        
        inputService.activate();

        
        multiplexer.addProcessor(inputService);
        multiplexer.addProcessor(hotbarUI.getStage());
        multiplexer.addProcessor(hudStage);
        multiplexer.addProcessor(chatTable.getStage());

        
        Gdx.input.setInputProcessor(multiplexer);

        audioService.playMenuMusic();
        initializePlayerPosition();
    }
    private void initializeUI() {
        pauseStage = new Stage(new ScreenViewport());
        pauseSkin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        pauseOverlay = new Image(new NinePatch(pauseSkin.getSprite("white"), 0, 0, 0, 0));
        pauseOverlay.setColor(new Color(0, 0, 0, 0.6f));
        pauseOverlay.setFillParent(true);
        pauseOverlay.setVisible(false);
        pauseStage.addActor(pauseOverlay);

        setupPauseMenu();

        hudStage = new Stage(new ScreenViewport());
        hudSkin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        chatTable = new ChatTable(hudSkin, chatService);
        chatTable.setPosition(10, Gdx.graphics.getHeight() - 210);
        chatTable.setSize(400, 200);
        hudStage.addActor(chatTable);


        Gdx.input.setInputProcessor(multiplexer);
    }

    private void setupPauseMenu() {
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = pauseSkin.getFont("default-font");
        windowStyle.titleFontColor = Color.WHITE;
        windowStyle.background = pauseSkin.newDrawable("white", new Color(0, 0, 0, 0.7f));

        Window pauseWindow = new Window("Paused", windowStyle);
        pauseWindow.setModal(true);
        pauseWindow.setMovable(false);

        TextButton resumeButton = new TextButton("Resume", pauseSkin);
        TextButton settingsButton = new TextButton("Settings", pauseSkin);
        TextButton exitButton = new TextButton("Exit to Menu", pauseSkin);

        resumeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                togglePause();
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                screenManager.disposeScreen(SettingsScreen.class);
                screenManager.showScreen(SettingsScreen.class);
            }
        });


        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                goBackToMenu();
            }
        });


        pauseWindow.row().pad(10);
        pauseWindow.add(resumeButton).width(180).height(40).pad(5).row();
        pauseWindow.add(settingsButton).width(180).height(40).pad(5).row();
        pauseWindow.add(exitButton).width(180).height(40).pad(5).row();

        pauseWindow.pack();
        pauseWindow.setPosition(
            (pauseStage.getViewport().getWorldWidth() - pauseWindow.getWidth()) / 2f,
            (pauseStage.getViewport().getWorldHeight() - pauseWindow.getHeight()) / 2f
        );
        pauseWindow.setVisible(false);

        pauseStage.addActor(pauseWindow);
        pauseOverlay.setUserObject(pauseWindow);
    }

    private void togglePause() {
        paused = !paused;
        Window pauseWindow = (Window) pauseOverlay.getUserObject();
        pauseOverlay.setVisible(paused);
        pauseWindow.setVisible(paused);

        if (paused) {
            multiplexer.addProcessor(0, pauseStage);


            worldService.saveWorldData();
        } else {
            multiplexer.removeProcessor(pauseStage);
        }
    }

    private void updateChunkLoading() {
        long now = System.currentTimeMillis();
        if (now - lastChunkUpdate > CHUNK_UPDATE_INTERVAL) {
            lastChunkUpdate = now;

            PlayerData player = playerService.getPlayerData();
            if (player != null) {
                
                chunkLoadingManager.preloadChunksAroundPosition(
                    player.getX(),
                    player.getY()
                );
            }

            
            chunkLoadingManager.update();
        }
    }

    @Override
    public void render(float delta) {
        
        handleDisconnection();

        if (!handlingDisconnect) {

            
            updateChunkLoading();

            
            if (!paused) {

                updateGame(delta);
            }

            
            renderGame(delta);
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            showDebug = !showDebug;
        }

        if (!chatService.isActive() && Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            chatTable.activate();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (chatService.isActive()) {
                chatTable.deactivate();
            } else {
                togglePause();
            }
        }
    }


    private void initializePlayerPosition() {
        String playerName = playerService.getPlayerData().getUsername();
        PlayerData pd = worldService.getPlayerData(playerName);
        log.debug("initializePlayerPosition -> from worldService: username={}, x={}, y={}",
            pd != null ? pd.getUsername() : "(null)",
            pd != null ? pd.getX() : 0f,
            pd != null ? pd.getY() : 0f);

        if (pd == null) {
            log.error("Could not load player data for {}", playerName);
            return;
        }

        
        float maxCoord = 1000000f;
        float boundedX = Math.max(-maxCoord, Math.min(maxCoord, pd.getX()));
        float boundedY = Math.max(-maxCoord, Math.min(maxCoord, pd.getY()));

        
        float playerPixelX = boundedX * TILE_SIZE;
        float playerPixelY = boundedY * TILE_SIZE;

        
        if (Float.isNaN(playerPixelX) || Float.isInfinite(playerPixelX) ||
            Float.isNaN(playerPixelY) || Float.isInfinite(playerPixelY)) {
            log.error("Invalid position conversion: {},{} -> {},{}",
                boundedX, boundedY, playerPixelX, playerPixelY);
            playerPixelX = 0;
            playerPixelY = 0;
        }

        
        cameraPosX = playerPixelX;
        cameraPosY = playerPixelY;
        camera.position.set(cameraPosX, cameraPosY, 0);
        camera.update();

        
        playerService.setPosition((int) (boundedX), (int) (boundedY));

        
        chunkLoadingManager.preloadChunksAroundPosition(boundedX, boundedY);

        
    }

    private void teleportPlayer(float x, float y) {
        
        chunkLoadingManager.preloadChunksAroundPosition(x, y);

        
        PlayerData player = playerService.getPlayerData();
        player.setX(x);
        player.setY(y);
        playerService.setPosition((int) x, (int) y);

        
        float pixelX = x * TILE_SIZE;
        float pixelY = y * TILE_SIZE;
        cameraPosX = pixelX;
        cameraPosY = pixelY;
        camera.position.set(cameraPosX, cameraPosY, 0);
        camera.update();

        
        if (multiplayerClient.isConnected()) {
            multiplayerClient.sendPlayerMove(
                x, y,
                player.isWantsToRun(),
                false,
                player.getDirection().name().toLowerCase()
            );
        }
    }

    private void updateGame(float delta) {
        if (!paused) {
            handleInput();
            PlayerData player = playerService.getPlayerData();
            updateCamera();
            chunkLoaderService.updatePlayerPosition(
                player.getX() * TILE_SIZE,
                player.getY() * TILE_SIZE
            );
            playerService.update(delta);

        }

        pauseStage.act(delta);
        hudStage.act(delta);
        hotbarUI.update();
        audioService.update(delta);
    }

    private void renderRemotePlayers(SpriteBatch batch, float delta) {
        Map<String, PlayerSyncData> states = multiplayerClient.getPlayerStates();
        String localUsername = playerService.getPlayerData().getUsername();

        
        for (Map.Entry<String, PlayerSyncData> entry : states.entrySet()) {
            String otherUsername = entry.getKey();
            if (otherUsername.equals(localUsername)) continue;

            PlayerSyncData psd = entry.getValue();

            RemotePlayerAnimator animator = remotePlayerAnimators.computeIfAbsent(
                otherUsername,
                k -> {
                    RemotePlayerAnimator newAnimator = new RemotePlayerAnimator();
                    
                    newAnimator.setPosition(psd.getX() * TILE_SIZE, psd.getY() * TILE_SIZE);
                    return newAnimator;
                }
            );

            
            float targetTileX = psd.getX() * TILE_SIZE;
            float targetTileY = psd.getY() * TILE_SIZE;

            
            animator.updateState(
                targetTileX,
                targetTileY,
                psd.isRunning(),
                PlayerDirection.valueOf(psd.getDirection().toUpperCase()),
                psd.isMoving() && isTrulyMoving(psd), 
                delta
            );

            
            TextureRegion frame = animationService.getCurrentFrame(
                animator.getDirection(),
                animator.isMoving(),
                animator.isRunning(),
                animator.getAnimationTime()
            );

            
            float drawX = animator.getCurrentX();
            float drawY = animator.getCurrentY();

            float width = frame.getRegionWidth();
            float height = frame.getRegionHeight();
            batch.draw(frame, drawX, drawY, width, height);

        }

        
        remotePlayerAnimators.keySet().removeIf(username -> !states.containsKey(username));
    }

    
    private boolean isTrulyMoving(PlayerSyncData psd) {
        float lastX = psd.getLastX();
        float lastY = psd.getLastY();
        float currentX = psd.getX();
        float currentY = psd.getY();

        float dx = Math.abs(currentX - lastX);
        float dy = Math.abs(currentY - lastY);

        return dx > 0.001f || dy > 0.001f;
    }

    private void renderGame(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        
        worldRenderer.render(camera, delta);

        
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        playerService.render(batch);

        
        renderRemotePlayers(batch, delta);
        batch.end();

        worldRenderer.renderTreeTops(delta);
        inventoryScreen.render(delta);
        

        
        if (paused) {
            pauseStage.draw();
        }

        hudStage.act(delta);
        hudStage.draw();
        hotbarUI.render();

        if (showDebug) {
            renderDebugInfo();
        }
    }

    private void updateCamera() {
        float playerPixelX = playerService.getPlayerData().getX() * TILE_SIZE + TILE_SIZE / 2f;
        float playerPixelY = playerService.getPlayerData().getY() * TILE_SIZE + TILE_SIZE / 2f;

        cameraPosX = lerp(cameraPosX, playerPixelX);
        cameraPosY = lerp(cameraPosY, playerPixelY);

        cameraPosX = Math.round(cameraPosX);
        cameraPosY = Math.round(cameraPosY);

        camera.position.set(cameraPosX, cameraPosY, 0);
        camera.update();
    }


    private void renderDebugInfo() {
        batch.setProjectionMatrix(hudStage.getCamera().combined);
        batch.begin();

        PlayerData player = playerService.getPlayerData();
        final int TILE_SIZE = 32;
        final int CHUNK_SIZE = 16;
        final int WORLD_WIDTH_TILES = 100000;
        final int WORLD_HEIGHT_TILES = 100000;

        float pixelX = player.getX() * TILE_SIZE;
        float pixelY = player.getY() * TILE_SIZE;
        int tileX = (int) player.getX();
        int tileY = (int) player.getY();
        int chunkX = tileX / CHUNK_SIZE;
        int chunkY = tileY / CHUNK_SIZE;
        int totalChunksX = WORLD_WIDTH_TILES / CHUNK_SIZE;
        int totalChunksY = WORLD_HEIGHT_TILES / CHUNK_SIZE;

        font.setColor(Color.WHITE);
        float y = 25;

        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 10, y);
        y += 20;
        font.draw(batch, String.format("Pixel Pos: (%.1f, %.1f)", pixelX, pixelY), 10, y);
        y += 20;
        font.draw(batch, String.format("Tile Pos: (%d, %d)", tileX, tileY), 10, y);
        y += 20;
        font.draw(batch, String.format("Chunk Pos: (%d, %d)", chunkX, chunkY), 10, y);
        y += 20;
        font.draw(batch, String.format("Total Tiles: %d x %d", WORLD_WIDTH_TILES, WORLD_HEIGHT_TILES), 10, y);
        y += 20;
        font.draw(batch, String.format("Total Chunks: %d x %d", totalChunksX, totalChunksY), 10, y);
        y += 20;
        font.draw(batch, "Biome: " + getBiomeName(pixelX, pixelY), 10, y);
        y += 20;
        font.draw(batch, "Direction: " + player.getDirection(), 10, y);

        batch.end();
    }

    private String getBiomeName(float pixelX, float pixelY) {
        var biomeResult = biomeService.getBiomeAt(pixelX, pixelY);
        return biomeResult.getPrimaryBiome() != null ? biomeResult.getPrimaryBiome().getName() : "Unknown";
    }

    private float lerp(float a, float b) {
        float CAMERA_LERP_FACTOR = 0.1f;
        return a + (b - a) * CAMERA_LERP_FACTOR;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
        camera.viewportHeight = camera.viewportWidth * ((float) height / width);
        camera.update();

        pauseStage.getViewport().update(width, height, true);
        hudStage.getViewport().update(width, height, true);

        hotbarUI.resize(width, height);
        if (chatTable != null) {
            chatTable.setPosition(10, height - 210);
        }
        hotbarUI.resize(width, height);

        if (pauseOverlay != null && pauseOverlay.isVisible()) {
            Window pauseWindow = (Window) pauseOverlay.getUserObject();
            if (pauseWindow != null) {
                pauseWindow.pack();
                pauseWindow.setPosition(
                    (pauseStage.getViewport().getWorldWidth() - pauseWindow.getWidth()) / 2f,
                    (pauseStage.getViewport().getWorldHeight() - pauseWindow.getHeight()) / 2f
                );
            }
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        if (multiplayerClient.isConnected()) {
            
            multiplayerClient.disconnect();
            
            worldService.handleDisconnect();
        }

        
        inputService.deactivate();

        
        if (multiplexer != null) {
            Gdx.input.setInputProcessor(null);
        }

        
        if (chatService != null && chatTable != null) {
            chatTable.deactivate();
        }

        
        audioService.stopMenuMusic();

        
        handlingDisconnect = false;
    }
    private void goBackToMenu() {
        
        inputService.deactivate();

        
        if (multiplexer != null) {
            Gdx.input.setInputProcessor(null);
            multiplexer = null;
        }

        if (multiplayerClient.isConnected()) {
            multiplayerClient.disconnect();
            worldService.handleDisconnect();
        }

        
        if (hotbarUI != null) {
            hotbarUI.dispose();
        }

        if (inventoryScreen != null) {
            inventoryScreen.dispose();
        }

        if (chatTable != null) {
            chatTable.deactivate();
        }

        
        worldService.clearWorldData();
        worldService.setMultiplayerMode(false);

        
        connectionManager.releaseInstanceLock();

        
        screenManager.showScreen(ModeSelectionScreen.class);
    }

    @Override
    public void dispose() {
        if (multiplayerClient.isConnected()) {
            multiplayerClient.disconnect();
            log.info("Disconnected from server during screen disposal");
        }

        inputService.deactivate();

        hotbarUI.dispose();
        inventoryScreen.dispose();

        if (batch != null) {
            batch.dispose();
            batch = null;
        }

        if (font != null) {
            font.dispose();
            font = null;
        }

        if (pauseStage != null) {
            pauseStage.dispose();
            pauseStage = null;
        }

        if (hudStage != null) {
            hudStage.dispose();
            hudStage = null;
        }

        if (pauseSkin != null) {
            pauseSkin.dispose();
            pauseSkin = null;
        }

        if (hudSkin != null) {
            hudSkin.dispose();
            hudSkin = null;
        }

        if (worldRenderer != null) {
            worldRenderer.dispose();
        }

        
        chunkLoadingManager.dispose();

        
        if (multiplexer != null) {
            multiplexer = null;
        }
    }
}
