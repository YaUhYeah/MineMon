package io.github.minemon.core.screen;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.chat.ui.ChatTable;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.input.InputService;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.model.WorldRenderer;
import io.github.minemon.world.service.ChunkLoaderService;
import io.github.minemon.world.service.ChunkPreloaderService;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class GameScreen implements Screen {
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
    private final ChunkPreloaderService chunkPreloaderService;
    private final MultiplayerClient multiplayerClient;
    private final PlayerAnimationService animationService;
    private OrthographicCamera camera;
    private SpriteBatch batch;
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
                      ChunkPreloaderService chunkPreloaderService, PlayerAnimationService animationService, MultiplayerClient client) {
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
        this.chunkPreloaderService = chunkPreloaderService;
    }

    @Override
    public void show() {
        log.debug("GameScreen.show() >> current worldName={}, seed={}",
            worldService.getWorldData().getWorldName(),
            worldService.getWorldData().getSeed());

        // Who is the current player?
        PlayerData pd = playerService.getPlayerData().getUsername() != null
            ? playerService.getPlayerData()
            : null;
        if (pd != null) {
            log.debug("Player data: username={}, x={}, y={}",
                pd.getUsername(), pd.getX(), pd.getY());
        }
        animationService.initAnimationsIfNeeded();
        if (worldRenderer != null) {
            worldRenderer.initialize();
        }

        float baseWidth = TARGET_VIEWPORT_WIDTH_TILES * TILE_SIZE;
        float aspect = (float) Gdx.graphics.getHeight() / (float) Gdx.graphics.getWidth();
        float baseHeight = baseWidth * aspect;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, baseWidth, baseHeight);
        worldService.setCamera(camera);

        batch = new SpriteBatch();
        font = new BitmapFont();

        audioService.playMenuMusic();
        initializeUI();
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

        multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(hudStage);
        multiplexer.addProcessor(inputService);

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
    private void goBackToMenu() {
        if (!worldService.isMultiplayerMode()) {
            worldService.saveWorldData();
        }

        if (multiplayerClient.isConnected()) {
            multiplayerClient.disconnect();
            log.info("Disconnected from server");
        }

        togglePause();
        screenManager.showScreen(ModeSelectionScreen.class);
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

    private void initializePlayerPosition() {
        String playerName = playerService.getPlayerData().getUsername();
        PlayerData pd = worldService.getPlayerData(playerName);
        log.debug("initializePlayerPosition -> from worldService: username={}, x={}, y={}",
            pd != null ? pd.getUsername() : "(null)",
            pd != null ? pd.getX() : 0f,
            pd != null ? pd.getY() : 0f);

        if (pd == null) {
            pd = new PlayerData(playerName, 0, 0);
            playerService.setPlayerData(pd);
            log.debug("No existing PD => created new at (0,0)");
        } else {
            log.debug("Setting position to PD's tile coords = ({}, {})", (int) pd.getX(), (int) pd.getY());
            playerService.setPosition((int) pd.getX(), (int) pd.getY());
        }

        float playerPixelX = pd.getX() * TILE_SIZE + TILE_SIZE / 2f;
        float playerPixelY = pd.getY() * TILE_SIZE + TILE_SIZE / 2f;
        cameraPosX = playerPixelX;
        cameraPosY = playerPixelY;
        camera.position.set(cameraPosX, cameraPosY, 0);
        camera.update();
    }


    @Override
    public void render(float delta) {
        handleInput();
        updateGame(delta);
        preloadChunksAhead();
        renderGame(delta);
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

    private void preloadChunksAhead() {
        PlayerData player = playerService.getPlayerData();
        float px = player.getX() * TILE_SIZE;
        float py = player.getY() * TILE_SIZE;
        chunkPreloaderService.preloadAround(px, py);
    }

    private void updateGame(float delta) {
        if (!paused) {
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
    }


    private void renderGame(float delta) {
        // Clear
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Draw world
        worldRenderer.render(camera, delta);

        // 1) Draw local player
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        playerService.render(batch);

        // 2) Draw remote players with updated animation time
        // In renderGame:
        Map<String, PlayerSyncData> states = multiplayerClient.getPlayerStates();
        String localUsername = playerService.getPlayerData().getUsername();

        for (Map.Entry<String, PlayerSyncData> entry : states.entrySet()) {
            String otherUsername = entry.getKey();
            // Skip local
            if (otherUsername.equals(localUsername)) continue;

            PlayerSyncData psd = entry.getValue();

            // If the remote is moving, increment their local animation timer
            if (psd.isMoving()) {
                psd.setAnimationTime(psd.getAnimationTime() + delta);
            }

            float px = psd.getX() * TILE_SIZE;
            float py = psd.getY() * TILE_SIZE;

            PlayerDirection dir = PlayerDirection.DOWN;
            try {
                dir = PlayerDirection.valueOf(psd.getDirection().toUpperCase());
            } catch (Exception ignored) {
            }

            // We pass 'psd.isMoving()' to get the correct standing vs. walking/running frames
            TextureRegion frame = animationService.getCurrentFrame(
                dir,
                psd.isMoving(),
                psd.isRunning(),
                psd.getAnimationTime()
            );
            batch.draw(frame, px, py);
        }


        batch.end();

        // Additional layered rendering if needed
        worldRenderer.renderTreeTops(delta);

        // Draw pause overlay if paused
        if (paused) {
            pauseStage.draw();
        }

        hudStage.act(delta);
        hudStage.draw();

        // Optional debug info
        if (showDebug) {
            renderDebugInfo();
        }
    }

    private void updateCamera() {
        float playerPixelX = playerService.getPlayerData().getX() * TILE_SIZE + TILE_SIZE / 2f;
        float playerPixelY = playerService.getPlayerData().getY() * TILE_SIZE + TILE_SIZE / 2f;

        cameraPosX = lerp(cameraPosX, playerPixelX);
        cameraPosY = lerp(cameraPosY, playerPixelY);

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

        if (chatTable != null) {
            chatTable.setPosition(10, height - 210);
        }

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
    }


    @Override
    public void dispose() {
        if (multiplayerClient.isConnected()) {
            multiplayerClient.disconnect();
            log.info("Disconnected from server during screen disposal");
        }

        batch.dispose();
        font.dispose();
        pauseStage.dispose();
        hudStage.dispose();
        pauseSkin.dispose();
        hudSkin.dispose();
        worldRenderer.dispose();
        chunkLoaderService.dispose();
        chunkPreloaderService.dispose();
    }
}
