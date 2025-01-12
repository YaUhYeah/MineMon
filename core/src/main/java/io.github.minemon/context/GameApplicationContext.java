package io.github.minemon.context;

import io.github.minemon.GdxGame;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.audio.service.impl.AudioServiceImpl;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.chat.service.impl.ChatServiceImpl;
import io.github.minemon.chat.service.impl.CommandServiceImpl;
import io.github.minemon.core.config.GameConfig;
import io.github.minemon.core.screen.GameScreen;
import io.github.minemon.core.screen.LoginScreen;
import io.github.minemon.core.screen.ModeSelectionScreen;
import io.github.minemon.core.screen.WorldSelectionScreen;
import io.github.minemon.core.service.*;
import io.github.minemon.core.service.impl.LocalFileAccessService;
import io.github.minemon.core.service.impl.ScreenManagerImpl;
import io.github.minemon.event.EventBus;
import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.input.InputConfiguration;
import io.github.minemon.input.InputService;
import io.github.minemon.inventory.service.impl.InventoryServiceImpl;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.multiplayer.service.ServerConnectionService;
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
import io.github.minemon.world.service.impl.ClientTileManagerImpl;
import io.github.minemon.world.service.impl.ClientWorldServiceImpl;
import io.github.minemon.world.service.impl.JsonWorldDataService;
import io.github.minemon.world.service.impl.ObjectTextureManager;
import io.github.minemon.world.service.impl.WorldGeneratorImpl;
import io.github.minemon.world.service.impl.WorldObjectManagerImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

@Slf4j
public class GameApplicationContext {
    private static ApplicationContext context;
    private static boolean initialized = false;


    public static void initContext(boolean isAndroid) {
        if (initialized) {
            log.warn("Context already initialized");
            return;
        }

        try {
            if (isAndroid) {
                initAndroidContext();
            } else {
                initDesktopContext();
            }
            initialized = true;
        } catch (Exception e) {
            log.error("Failed to initialize context: {}", e.getMessage(), e);
            throw new RuntimeException("Context initialization failed", e);
        }
    }

    private static void initAndroidContext() {

        GenericApplicationContext androidCtx = new AndroidSafeApplicationContext();
        DefaultListableBeanFactory bf = (DefaultListableBeanFactory) androidCtx.getBeanFactory();


        registerAndroidBeans(bf, androidCtx);


        androidCtx.refresh();
        context = androidCtx;
        log.info("Android context initialized successfully");
    }


    private static void registerAndroidBeans(DefaultListableBeanFactory bf, GenericApplicationContext androidCtx) {

        bf.registerSingleton("fileAccessService", new LocalFileAccessService());
        bf.registerSingleton("inputConfiguration", new InputConfiguration());
        bf.registerSingleton("playerProperties", new PlayerProperties());
        bf.registerSingleton("eventBus", new EventBus());
        bf.registerSingleton("gdxGame", new GdxGame());


        bf.registerSingleton("worldConfig", new WorldConfig(System.currentTimeMillis()));
        bf.registerSingleton("gameConfig", new GameConfig());


        bf.registerSingleton("settingsService",
            new SettingsService(bf.getBean(InputConfiguration.class))
        );


        bf.registerSingleton("jsonWorldDataService", new JsonWorldDataService("save/worlds", false));


        bf.registerSingleton("biomeConfigurationLoader",
            new BiomeConfigurationLoader(bf.getBean(FileAccessService.class))
        );
        bf.registerSingleton("biomeService",
            new BiomeServiceImpl(bf.getBean(BiomeConfigurationLoader.class))
        );
        bf.registerSingleton("objectTextureManager", new ObjectTextureManager());
        bf.registerSingleton("worldObjectManager", new WorldObjectManagerImpl());
        bf.registerSingleton("tileManager", new ClientTileManagerImpl(
            bf.getBean(FileAccessService.class)
        ));
        bf.registerSingleton("worldGenerator", new WorldGeneratorImpl(
            bf.getBean(WorldConfig.class)
        ));
        bf.registerSingleton("worldService", new ClientWorldServiceImpl(
            bf.getBean(WorldConfig.class),
            bf.getBean(WorldGenerator.class),
            bf.getBean(WorldObjectManager.class),

            bf.getBean(TileManager.class),
            bf.getBean(BiomeConfigurationLoader.class),
            bf.getBean(BiomeServiceImpl.class),
            bf.getBean(ObjectTextureManager.class),
            bf.getBean(JsonWorldDataService.class),
            bf.getBean(FileAccessService.class)
        ));
        bf.registerSingleton("chunkPreloaderService", new ChunkPreloaderService(bf.getBean(WorldService.class)));

        bf.registerSingleton("chunkLoaderService", new ChunkLoaderService(bf.getBean(WorldService.class)));
        bf.registerSingleton("worldRenderer", new WorldRenderer(
            bf.getBean(WorldService.class),
            bf.getBean(TileManager.class),
            bf.getBean(ObjectTextureManager.class)
        ));
        bf.registerSingleton("chunkLoadingManager", new ChunkLoadingManager());


        bf.registerSingleton("inputService",
            new InputService(bf.getBean(InputConfiguration.class))
        );
        bf.registerSingleton("playerAnimationService", new PlayerAnimationServiceImpl());
        bf.registerSingleton("inventoryService", new InventoryServiceImpl());
        bf.registerSingleton("playerService", new PlayerServiceImpl(
            bf.getBean(PlayerAnimationServiceImpl.class),
            bf.getBean(InputService.class),
            bf.getBean(PlayerProperties.class),
            bf.getBean(WorldService.class),
            bf.getBean(InventoryServiceImpl.class)
        ));
        bf.registerSingleton("androidTouchInput", new AndroidTouchInput(bf.getBean(InputService.class)));

        bf.registerSingleton("uiService", new UiService());


        bf.registerSingleton("screenManager", new ScreenManagerImpl(
            androidCtx,
            bf.getBean(GdxGame.class)
        ));
        bf.registerSingleton("serverConnectionService", new ServerConnectionServiceImpl(bf.getBean(FileAccessService.class)));
        bf.registerSingleton("backgroundService", new BackgroundService());


        bf.registerSingleton("audioService", new AudioServiceImpl());

        bf.registerSingleton("applicationEventPublisher", new AndroidEventPublisher());

        bf.registerSingleton("commandService", new CommandServiceImpl());
        bf.registerSingleton("multiplayerClient", new MultiplayerClientImpl(
            bf.getBean("applicationEventPublisher", org.springframework.context.ApplicationEventPublisher.class)
        ));
        bf.registerSingleton("chatService", new ChatServiceImpl(
            bf.getBean("playerService", PlayerServiceImpl.class),
            bf.getBean("multiplayerClient", MultiplayerClientImpl.class),
            bf.getBean("commandService", CommandServiceImpl.class)
        ));


        ModeSelectionScreen modeSelectionScreen = new ModeSelectionScreen(
            bf.getBean(AudioService.class),
            bf.getBean(ScreenManager.class),
            bf.getBean(SettingsService.class),
            bf.getBean(BackgroundService.class)
        );
        modeSelectionScreen.setMultiplayerClient(bf.getBean(MultiplayerClient.class));
        modeSelectionScreen.setWorldService(bf.getBean(WorldService.class));
        WorldSelectionScreen worldSelectionScreen = new WorldSelectionScreen(
            bf.getBean(AudioService.class),
            bf.getBean(WorldService.class),
            bf.getBean(ScreenManager.class)
        );

        bf.registerSingleton("worldSelectionScreen", worldSelectionScreen);

        LoginScreen loginScreen = new LoginScreen(
            bf.getBean(AudioService.class),
            bf.getBean(ScreenManager.class),
            bf.getBean(ServerConnectionService.class),
            bf.getBean(MultiplayerClient.class),
            bf.getBean(UiService.class)
        );
        bf.registerSingleton("loginScreen", loginScreen);
        bf.registerSingleton("modeSelectionScreen", modeSelectionScreen);
        GameScreen gameScreen = new GameScreen(
            bf.getBean(PlayerService.class),
            bf.getBean(WorldService.class),
            bf.getBean(AudioService.class),
            bf.getBean(InputService.class),
            bf.getBean(ScreenManager.class),
            bf.getBean(ChatService.class),
            bf.getBean(BiomeService.class),
            bf.getBean(WorldRenderer.class),
            bf.getBean(ChunkLoaderService.class),
            bf.getBean(ChunkPreloaderService.class),
            bf.getBean(PlayerAnimationService.class),
            bf.getBean(MultiplayerClient.class),
            bf.getBean(ChunkLoadingManager.class)
        );
        bf.registerSingleton("gameScreen", gameScreen);
    }

    private static void initDesktopContext() {

        AnnotationConfigApplicationContext desktopCtx = new AnnotationConfigApplicationContext();
        desktopCtx.scan("io.github.minemon");
        desktopCtx.refresh();
        context = desktopCtx;
        log.info("Desktop context initialized with component scanning");
    }

    public static ApplicationContext getContext() {
        if (!initialized) {
            throw new IllegalStateException("Context not initialized. Call initContext() first");
        }
        return context;
    }

    public static <T> T getBean(Class<T> beanType) {
        return getContext().getBean(beanType);
    }

    public static void dispose() {
        try {
            if (context instanceof AutoCloseable) {
                ((AutoCloseable) context).close();
            }
        } catch (Exception e) {
            log.error("Error disposing context: {}", e.getMessage());
        }
        context = null;
        initialized = false;
        log.info("Context disposed");
    }
}
