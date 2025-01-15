package io.github.minemon.core.config;

import io.github.minemon.GdxGame;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.audio.service.impl.AudioServiceImpl;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.chat.service.impl.ChatServiceImpl;
import io.github.minemon.chat.service.impl.CommandServiceImpl;
import io.github.minemon.core.screen.*;
import io.github.minemon.core.service.*;
import io.github.minemon.core.service.impl.LocalFileAccessService;
import io.github.minemon.core.service.impl.ScreenManagerImpl;
import io.github.minemon.core.ui.HotbarUI;
import io.github.minemon.event.EventBus;
import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.input.InputConfiguration;
import io.github.minemon.input.InputService;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.inventory.service.impl.InventoryServiceImpl;
import io.github.minemon.inventory.service.impl.ItemPickupHandler;
import io.github.minemon.inventory.service.impl.ItemSpawnService;
import io.github.minemon.inventory.service.impl.ItemTextureManager;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.multiplayer.service.impl.ClientConnectionManager;
import io.github.minemon.multiplayer.service.impl.MultiplayerClientImpl;
import io.github.minemon.multiplayer.service.impl.ServerConnectionServiceImpl;
import io.github.minemon.player.config.PlayerProperties;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.player.service.impl.PlayerAnimationServiceImpl;
import io.github.minemon.player.service.impl.PlayerServiceImpl;
import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.biome.service.impl.BiomeServiceImpl;
import io.github.minemon.world.config.WorldConfig;
import io.github.minemon.world.model.WorldRenderer;
import io.github.minemon.world.service.*;
import io.github.minemon.world.service.impl.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration
public class AndroidConfig extends BaseGameConfig {

    @Bean
    @Primary
    public FileAccessService fileAccessService() {
        return new LocalFileAccessService();
    }

    @Bean
    public InputConfiguration inputConfiguration() {
        return new InputConfiguration();
    }

    @Bean
    public PlayerProperties playerProperties() {
        return new PlayerProperties();
    }

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    public GdxGame gdxGame() {
        return new GdxGame();
    }

    @Bean
    public WorldConfig worldConfig() {
        return new WorldConfig(System.currentTimeMillis());
    }

    @Bean
    public GameConfig gameConfig() {
        return new GameConfig();
    }

    @Bean
    public SettingsService settingsService(InputConfiguration inputConfiguration) {
        return new SettingsService(inputConfiguration);
    }

    @Bean
    public JsonWorldDataService jsonWorldDataService() {
        return new JsonWorldDataService("save/worlds", true);
    }

    @Bean
    public BiomeConfigurationLoader biomeConfigurationLoader(FileAccessService fileAccessService) {
        return new BiomeConfigurationLoader(fileAccessService);
    }

    @Bean
    public BiomeService biomeService(BiomeConfigurationLoader biomeConfigurationLoader) {
        return new BiomeServiceImpl(biomeConfigurationLoader);
    }

    @Bean
    public ObjectTextureManager objectTextureManager() {
        return new ObjectTextureManager();
    }

    @Bean
    public WorldObjectManager worldObjectManager() {
        return new WorldObjectManagerImpl();
    }

    @Bean
    public TileManager tileManager(FileAccessService fileAccessService) {
        return new ClientTileManagerImpl(fileAccessService);
    }

    @Bean
    public WorldGenerator worldGenerator(WorldConfig worldConfig) {
        return new WorldGeneratorImpl(worldConfig);
    }

    @Bean
    public WorldService worldService(WorldConfig worldConfig, WorldGenerator worldGenerator,
                                   WorldObjectManager worldObjectManager, TileManager tileManager,
                                   BiomeConfigurationLoader biomeConfigurationLoader,
                                   BiomeService biomeService, ObjectTextureManager objectTextureManager,
                                   JsonWorldDataService jsonWorldDataService,
                                   FileAccessService fileAccessService) {
        return new ClientWorldServiceImpl(worldConfig, worldGenerator, worldObjectManager,
                tileManager, biomeConfigurationLoader, (BiomeServiceImpl) biomeService,
                objectTextureManager, jsonWorldDataService, fileAccessService);
    }

    @Bean
    public ChunkPreloaderService chunkPreloaderService(WorldService worldService) {
        return new ChunkPreloaderService(worldService);
    }

    @Bean
    public ChunkLoaderService chunkLoaderService(WorldService worldService) {
        return new ChunkLoaderService(worldService);
    }

    @Bean
    public WorldRenderer worldRenderer(WorldService worldService, TileManager tileManager,
                                     ObjectTextureManager objectTextureManager) {
        return new WorldRenderer(worldService, tileManager, objectTextureManager);
    }

    @Bean
    public ChunkLoadingManager chunkLoadingManager() {
        return new ChunkLoadingManager();
    }

    @Bean
    public ItemTextureManager itemTextureManager() {
        return new ItemTextureManager();
    }

    @Bean
    public ItemSpawnService itemSpawnService(
        WorldService worldService,
        WorldObjectManager worldObjectManager,
        MultiplayerClient multiplayerClient
    ) {
        return new ItemSpawnService(worldService, worldObjectManager, multiplayerClient);
    }

    @Bean
    public ItemPickupHandler itemPickupHandler() {
        return new ItemPickupHandler();
    }

    @Bean
    public InventoryServiceImpl inventoryService() {
        return new InventoryServiceImpl();
    }

    @Bean
    public AndroidTouchInput androidTouchInput(InputService inputService) {
        return new AndroidTouchInput(inputService);
    }

    @Bean
    public InputService inputService(
        InputConfiguration inputConfiguration,
        ItemPickupHandler itemPickupHandler,
        ChatService chatService,
        MultiplayerClient multiplayerClient,
        @Lazy PlayerService playerService,
        InventoryScreen inventoryScreen,
        @Lazy WorldService worldService
    ) {
        InputService service = new InputService(
            inputConfiguration,
            itemPickupHandler,
            chatService,
            multiplayerClient,
            playerService,
            inventoryScreen,
            worldService
        );
        service.setAndroidMode(true);
        return service;
    }

    @Bean
    public PlayerAnimationService playerAnimationService() {
        return new PlayerAnimationServiceImpl();
    }

    @Bean
    public PlayerService playerService(PlayerAnimationService playerAnimationService,
                                     InputService inputService, PlayerProperties playerProperties,
                                     WorldService worldService, InventoryServiceImpl inventoryService) {
        return new PlayerServiceImpl((PlayerAnimationServiceImpl) playerAnimationService,
                inputService, playerProperties, worldService, inventoryService);
    }

    @Bean
    public UiService uiService() {
        return new UiService();
    }

    @Bean
    public ScreenManager screenManager(GdxGame gdxGame, ApplicationContext applicationContext) {
        return new ScreenManagerImpl(applicationContext, gdxGame);
    }

    @Bean
    public ServerConnectionService serverConnectionService(FileAccessService fileAccessService) {
        return new ServerConnectionServiceImpl(fileAccessService);
    }

    @Bean
    public BackgroundService backgroundService() {
        return new BackgroundService();
    }

    @Bean
    public AudioService audioService() {
        return new AudioServiceImpl();
    }

    @Bean
    public CommandServiceImpl commandService() {
        return new CommandServiceImpl();
    }

    @Bean
    public MultiplayerClient multiplayerClient(ApplicationEventPublisher applicationEventPublisher) {
        return new MultiplayerClientImpl(applicationEventPublisher);
    }

    @Bean
    public ChatService chatService(MultiplayerClient multiplayerClient,
                                 CommandServiceImpl commandService) {
        // Create a temporary PlayerService for ChatService
        // The real PlayerService will be injected later
        PlayerServiceImpl tempPlayerService = new PlayerServiceImpl(
            new PlayerAnimationServiceImpl(),
            null,  // InputService will be injected later
            new PlayerProperties(),
            null,  // WorldService will be injected later
            new InventoryServiceImpl()
        );
        return new ChatServiceImpl(tempPlayerService,
                (MultiplayerClientImpl) multiplayerClient, commandService);
    }

    @Bean
    public ClientConnectionManager clientConnectionManager() {
        return new ClientConnectionManager();
    }

    @Bean
    public HotbarUI hotbarUI(UiService uiService,
                            InventoryService inventoryService,
                            ItemTextureManager itemTextureManager) {
        return new HotbarUI(uiService, inventoryService, itemTextureManager);
    }

    @Bean
    public InventoryScreen inventoryScreen(InventoryService inventoryService,
                                         UiService uiService,
                                         @Lazy InputService inputService) {
        return new InventoryScreen(inventoryService, uiService, inputService);
    }

    @Bean
    public ModeSelectionScreen modeSelectionScreen(AudioService audioService,
                                                 ScreenManager screenManager,
                                                 SettingsService settingsService,
                                                 BackgroundService backgroundService,
                                                 MultiplayerClient multiplayerClient,
                                                 WorldService worldService) {
        ModeSelectionScreen screen = new ModeSelectionScreen(audioService, screenManager,
                settingsService, backgroundService);
        screen.setMultiplayerClient(multiplayerClient);
        screen.setWorldService(worldService);
        return screen;
    }

    @Bean
    public WorldSelectionScreen worldSelectionScreen(AudioService audioService,
                                                   WorldService worldService,
                                                   ScreenManager screenManager) {
        return new WorldSelectionScreen(audioService, worldService, screenManager);
    }

    @Bean
    public LoginScreen loginScreen(AudioService audioService, ScreenManager screenManager,
                                 ServerConnectionService serverConnectionService,
                                 MultiplayerClient multiplayerClient, UiService uiService) {
        return new LoginScreen(audioService, screenManager, serverConnectionService,
                multiplayerClient, uiService);
    }

    @Bean
    public GameScreen gameScreen(PlayerService playerService, WorldService worldService,
                               AudioService audioService, InputService inputService,
                               ScreenManager screenManager, ChatService chatService,
                               BiomeService biomeService, WorldRenderer worldRenderer,
                               ChunkLoaderService chunkLoaderService,
                               ChunkPreloaderService chunkPreloaderService,
                               PlayerAnimationService playerAnimationService,
                               MultiplayerClient multiplayerClient,
                               ChunkLoadingManager chunkLoadingManager,
                               ItemTextureManager itemTextureManager) {
        return new GameScreen(playerService, worldService, audioService, inputService,
                screenManager, chatService, biomeService, worldRenderer, chunkLoaderService,
                chunkPreloaderService, playerAnimationService, multiplayerClient,
                chunkLoadingManager, itemTextureManager);
    }
}
