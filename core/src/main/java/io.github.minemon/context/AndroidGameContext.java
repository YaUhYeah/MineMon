package io.github.minemon.context;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.GdxGame;
import io.github.minemon.chat.service.ChatService;
import io.github.minemon.core.config.GameConfig;
import io.github.minemon.core.screen.*;
import io.github.minemon.core.service.*;
import io.github.minemon.core.service.impl.AndroidAssetManager;
import io.github.minemon.core.service.impl.ScreenManagerImpl;
import io.github.minemon.core.ui.HotbarUI;
import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.input.InputConfiguration;
import io.github.minemon.input.InputService;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.inventory.service.impl.ItemTextureManager;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.multiplayer.service.impl.ClientConnectionManager;
import io.github.minemon.multiplayer.service.impl.ServerConnectionServiceImpl;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.model.WorldRenderer;
import io.github.minemon.world.service.*;
import io.github.minemon.world.service.impl.*;
import io.github.minemon.player.service.impl.PlayerServiceImpl;
import io.github.minemon.player.service.impl.PlayerAnimationServiceImpl;
import io.github.minemon.player.config.PlayerProperties;
import io.github.minemon.audio.service.impl.AudioServiceImpl;
import io.github.minemon.inventory.service.impl.InventoryServiceImpl;
import io.github.minemon.multiplayer.service.impl.MultiplayerClientImpl;
import io.github.minemon.chat.service.impl.ChatServiceImpl;
import io.github.minemon.chat.service.impl.CommandServiceImpl;
import io.github.minemon.world.biome.service.impl.BiomeServiceImpl;
import io.github.minemon.world.config.WorldConfig;
import io.github.minemon.core.service.impl.LocalFileAccessService;
import io.github.minemon.world.biome.config.BiomeConfigurationLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.*;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;

@Slf4j
public class AndroidGameContext {

    private static final Map<Class<?>, Object> beans = new HashMap<>();
    private static boolean minimalInitialized = false;
    private static boolean fullyInitialized = false;
    private static boolean registrationComplete = false;

    public static void initMinimal() {
        if (!minimalInitialized) {
            try {
                log.info("Initializing minimal Android game context");

                // Ensure LibGDX is properly initialized
                if (Gdx.app == null) {
                    log.error("LibGDX application must be initialized before calling initMinimal");
                    throw new IllegalStateException("Call initialize() on AndroidApplication first");
                }

                // Wait for GL context with a shorter timeout
                int attempts = 0;
                while (Gdx.graphics.getGL20() == null && attempts < 5) {
                    try {
                        Thread.sleep(200);
                        attempts++;
                        log.debug("Waiting for GL context, attempt {}/5", attempts);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Register core beans in a safe order
                registerCoreBeans();

                // Verify critical beans
                verifyCoreBeans();

                minimalInitialized = true;
                log.info("Minimal context initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize minimal context", e);
                cleanup();
                throw new RuntimeException("Context initialization failed: " + e.getMessage(), e);
            }
        }
    }

    private static void verifyCoreBeans() {
        log.info("Verifying core beans...");
        try {
            // Verify essential beans
            getBean(GameConfig.class);
            getBean(InputConfiguration.class);
            getBean(FileAccessService.class);
            getBean(ApplicationEventPublisher.class);

            log.info("Core beans verified successfully");
        } catch (Exception e) {
            log.error("Core bean verification failed", e);
            throw e;
        }
    }

    private static void registerCoreBeans() {
        try {
            // Core configuration
            GameConfig gameConfig = new GameConfig();
            register(gameConfig);

            WorldConfig worldConfig = new WorldConfig(System.currentTimeMillis());
            register(worldConfig);

            PlayerProperties playerProperties = new PlayerProperties();
            register(playerProperties);

            // Input handling
            InputConfiguration inputConfig = new InputConfiguration();
            register(inputConfig);

            InputService inputService = new InputService(inputConfig);
            register(inputService);

            AndroidTouchInput touchInput = new AndroidTouchInput(inputService);
            register(AndroidTouchInput.class, touchInput);

            // Event handling
            ApplicationEventPublisher eventPublisher = new AndroidEventPublisher();
            register(ApplicationEventPublisher.class, eventPublisher);


            FileAccessService fileAccessService = new LocalFileAccessService();
            register(fileAccessService);

            SettingsService settingsService = new SettingsService(inputConfig);
            register(settingsService);


            // GdxGame will be registered by the launcher


            registrationComplete = true;

        } catch (Exception e) {
            log.error("Failed to register core beans", e);
            cleanup();
            throw e;
        }
    }

    public static synchronized void register(Object bean) {
        Class<?> type = bean.getClass();
        beans.put(type, bean);
        for (Class<?> iface : type.getInterfaces()) {
            beans.put(iface, bean);
        }
        log.debug("Registered bean: {}", type.getSimpleName());
    }

    public static synchronized void register(Class<?> type, Object bean) {
        if (beans.containsKey(type)) {
            log.warn("Bean of type {} already registered, will be replaced", type.getSimpleName());
        }
        beans.put(type, bean);
        log.debug("Registered bean type: {}", type.getSimpleName());
    }

    public static synchronized boolean isRegistered(Class<?> type) {
        return beans.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(Class<T> type) {
        if (!registrationComplete) {
            log.error("Attempted to get bean of type {} before registration completed", type.getName());
            throw new IllegalStateException("Bean registration not complete. Did you call initMinimal()?");
        }
        T bean = (T) beans.get(type);
        if (bean == null) {
            log.error("No bean found of type: {}", type.getName());
            log.error("Available beans: {}", beans.keySet());
            throw new IllegalStateException("No bean of type " + type.getName() + " found");
        }
        return bean;
    }

    public static void initServices() {
        if (!minimalInitialized) {
            throw new IllegalStateException("Must call initMinimal first");
        }
        if (!fullyInitialized) {
            try {
                log.info("Initializing services");
                initializeServices();
                fullyInitialized = true;
                log.info("Services initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize services", e);
                cleanup();
                throw new RuntimeException("Service initialization failed", e);
            }
        }
    }

    private static void cleanup() {
        try {
            log.info("Cleaning up failed initialization");
            beans.clear();
            minimalInitialized = false;
            registrationComplete = false;
        } catch (Exception e) {
            log.error("Cleanup failed", e);
        }
    }

    private static void initializeServices() {
        if (Gdx.files == null) {
            throw new IllegalStateException("LibGDX not initialized");
        }

        try {

            AndroidAssetManager assetManager = new AndroidAssetManager();
            register(assetManager);
            assetManager.initialize();


            registerRemainingServices();


            getBean(SettingsService.class).initialize();
            getBean(TileManager.class).initIfNeeded();
            getBean(WorldObjectManager.class).initialize();
            getBean(WorldService.class).initIfNeeded();


            if (Gdx.graphics != null && Gdx.graphics.getGL20() != null) {
                getBean(UiService.class).initialize();
                getBean(ObjectTextureManager.class).initializeIfNeeded();
                getBean(PlayerAnimationService.class).initAnimationsIfNeeded();
                getBean(AudioService.class).initAudio();
                getBean(BiomeService.class).init();
            } else {
                log.warn("OpenGL context not ready, deferring GL-dependent service initialization");
            }

        } catch (Exception e) {
            log.error("Service initialization failed", e);
            throw e;
        }
    }

    private static void registerRemainingServices() {
        try {
            // Core UI Services
            UiService uiService = new UiService();
            register(uiService);

            BackgroundService backgroundService = new BackgroundService();
            register(backgroundService);

            ObjectTextureManager objectTextureManager = new ObjectTextureManager();
            register(objectTextureManager);


            BiomeConfigurationLoader biomeLoader = new BiomeConfigurationLoader(getBean(FileAccessService.class));
            register(biomeLoader);

            BiomeServiceImpl biomeService = new BiomeServiceImpl(biomeLoader);
            register(biomeService);

            WorldObjectManagerImpl worldObjectManager = new WorldObjectManagerImpl();
            register(worldObjectManager);

            ClientTileManagerImpl tileManager = new ClientTileManagerImpl(getBean(FileAccessService.class));
            register(tileManager);

            CommandServiceImpl commandService = new CommandServiceImpl();
            register(commandService);


            MultiplayerClientImpl multiplayerClient = new MultiplayerClientImpl(getBean(ApplicationEventPublisher.class));
            register(multiplayerClient);


            InventoryServiceImpl inventoryService = new InventoryServiceImpl();
            register(inventoryService);


            String worldsDir = System.getProperty("user.home", ".") + "/save/worlds";
            JsonWorldDataService jsonWorldDataService = new JsonWorldDataService(worldsDir, false);
            register(jsonWorldDataService);

            WorldGeneratorImpl worldGenerator = new WorldGeneratorImpl(getBean(WorldConfig.class));
            register(worldGenerator);
            FileAccessService fileAccessService = new LocalFileAccessService();

            ClientWorldServiceImpl worldService = new ClientWorldServiceImpl(
                getBean(WorldConfig.class),
                worldGenerator,
                worldObjectManager,
                tileManager,
                biomeLoader,
                biomeService,
                objectTextureManager,
                jsonWorldDataService, fileAccessService
            );
            register(worldService);



            PlayerAnimationServiceImpl playerAnimationService = new PlayerAnimationServiceImpl();
            register(playerAnimationService);

            PlayerServiceImpl playerService = new PlayerServiceImpl(
                playerAnimationService,
                getBean(InputService.class),
                getBean(PlayerProperties.class),
                worldService,
                inventoryService
            );
            register(playerService);


            ChunkLoadingManager chunkLoadingManager = new ChunkLoadingManager();
            chunkLoadingManager.setWorldService(getBean(WorldService.class));
            chunkLoadingManager.setMultiplayerClient(getBean(MultiplayerClient.class));
            register(chunkLoadingManager);

            AudioServiceImpl audioService = new AudioServiceImpl();
            register(audioService);
            WorldRenderer worldRenderer = new WorldRenderer(
                getBean(WorldService.class),
                getBean(TileManager.class),
                getBean(ObjectTextureManager.class)
            );
            register(worldRenderer);
            ServerConnectionService serverConnectionService = new ServerConnectionServiceImpl(fileAccessService);


            ChatServiceImpl chatService = new ChatServiceImpl(
                playerService,
                multiplayerClient,
                commandService
            );
            register(chatService);

            ApplicationEventPublisher eventPublisher = getBean(ApplicationEventPublisher.class);
            AndroidApplicationContext applicationContext = new AndroidApplicationContext(eventPublisher);
            register(ApplicationContext.class, applicationContext);
            ItemTextureManager itemTextureManager = new ItemTextureManager();
            register(ItemTextureManager.class, itemTextureManager);

            ScreenManagerImpl screenManager = new ScreenManagerImpl(applicationContext, getBean(GdxGame.class));
            register(screenManager);
            ChunkLoaderService chunkLoaderService = new ChunkLoaderService(getBean(WorldService.class));
            register(chunkLoaderService);
            ChunkPreloaderService chunkPreloaderService = new ChunkPreloaderService(getBean(WorldService.class));
            register(chunkPreloaderService);
            ClientConnectionManager clientConnectionManager = new ClientConnectionManager();
            register(clientConnectionManager);

            HotbarUI hotbarUI = new HotbarUI(getBean(UiService.class), getBean(InventoryService.class), getBean(ItemTextureManager.class));
            register(hotbarUI);
            // Register all screens
            ModeSelectionScreen modeSelectionScreen = new ModeSelectionScreen(
                getBean(AudioService.class),
                screenManager,
                getBean(SettingsService.class),
                getBean(BackgroundService.class)
            );
            modeSelectionScreen.setMultiplayerClient(getBean(MultiplayerClient.class));
            modeSelectionScreen.setWorldService(getBean(WorldService.class));

            register(ModeSelectionScreen.class, modeSelectionScreen);

            InventoryScreen inventoryScreen = new InventoryScreen(
                getBean(InventoryService.class),
                getBean(UiService.class),
                getBean(InputService.class)
            );
            register(InventoryScreen.class, inventoryScreen);
            GameScreen gameScreen = new GameScreen(
                getBean(PlayerService.class),
                getBean(WorldService.class),
                getBean(AudioService.class),
                getBean(InputService.class),
                screenManager,
                getBean(ChatService.class),
                getBean(BiomeService.class),
                getBean(WorldRenderer.class),
                getBean(ChunkLoaderService.class),
                getBean(ChunkPreloaderService.class),
                getBean(PlayerAnimationService.class),
                getBean(MultiplayerClient.class),
                getBean(ChunkLoadingManager.class)
            );
            gameScreen.setHotbarUI(getBean(HotbarUI.class));
            gameScreen.setWorldService(getBean(WorldService.class));
            gameScreen.setInventoryScreen(getBean(InventoryScreen.class));
            gameScreen.setItemTextureManager(getBean(ItemTextureManager.class));
            gameScreen.setConnectionManager(getBean(ClientConnectionManager.class));
            register(GameScreen.class, gameScreen);

            WorldSelectionScreen worldSelectionScreen = new WorldSelectionScreen(
                getBean(AudioService.class),
                getBean(WorldService.class),
                screenManager
            );
            register(WorldSelectionScreen.class, worldSelectionScreen);

            LoginScreen loginScreen = new LoginScreen(
                getBean(AudioService.class),
                getBean(ScreenManager.class),
                serverConnectionService,
                getBean(MultiplayerClient.class),
                getBean(UiService.class)
            );
            register(LoginScreen.class, loginScreen);


            SettingsScreen settingsScreen = new SettingsScreen(
                screenManager,
                getBean(SettingsService.class),
                getBean(BackgroundService.class)
            );
            register(SettingsScreen.class, settingsScreen);

            ServerDisconnectScreen serverDisconnectScreen = new ServerDisconnectScreen(
                screenManager,
                getBean(UiService.class)
            );
            register(ServerDisconnectScreen.class, serverDisconnectScreen);

            log.debug("All remaining services registered successfully");

        } catch (Exception e) {
            log.error("Failed to register remaining services", e);
            throw new RuntimeException("Service registration failed", e);
        }
    }

    private static void registerBeans() {
        log.debug("Starting bean registration");


        GameConfig gameConfig = new GameConfig();
        register(gameConfig);

        WorldConfig worldConfig = new WorldConfig(System.currentTimeMillis());
        register(worldConfig);

        PlayerProperties playerProperties = new PlayerProperties();
        register(playerProperties);

        InputConfiguration inputConfig = new InputConfiguration();
        register(inputConfig);


        ApplicationEventPublisher eventPublisher = new AndroidEventPublisher();
        register(ApplicationEventPublisher.class, eventPublisher);


        FileAccessService fileAccessService = new LocalFileAccessService();
        register(fileAccessService);

        InputService inputService = new InputService(inputConfig);
        register(inputService);

        SettingsService settingsService = new SettingsService(inputConfig);
        register(settingsService);

        UiService uiService = new UiService();
        register(uiService);


        BiomeConfigurationLoader biomeLoader = new BiomeConfigurationLoader(fileAccessService);
        register(biomeLoader);

        BiomeServiceImpl biomeService = new BiomeServiceImpl(biomeLoader);
        register(biomeService);

        ObjectTextureManager objectTextureManager = new ObjectTextureManager();
        register(objectTextureManager);

        WorldObjectManagerImpl worldObjectManager = new WorldObjectManagerImpl();
        register(worldObjectManager);

        ClientTileManagerImpl tileManager = new ClientTileManagerImpl(fileAccessService);
        register(tileManager);

        CommandServiceImpl commandService = new CommandServiceImpl();
        register(commandService);


        MultiplayerClientImpl multiplayerClient = new MultiplayerClientImpl(eventPublisher);
        register(multiplayerClient);


        InventoryServiceImpl inventoryService = new InventoryServiceImpl();
        register(inventoryService);


        String worldsDir = System.getProperty("user.home", ".") + "/save/worlds";
        JsonWorldDataService jsonWorldDataService = new JsonWorldDataService(worldsDir, false);
        register(jsonWorldDataService);

        WorldGeneratorImpl worldGenerator = new WorldGeneratorImpl(worldConfig);
        register(worldGenerator);


        ClientWorldServiceImpl worldService = new ClientWorldServiceImpl(
            worldConfig, worldGenerator, worldObjectManager,
            tileManager, biomeLoader, biomeService,
            objectTextureManager, jsonWorldDataService, fileAccessService
        );
        register(worldService);

        ChunkLoadingManager chunkLoadingManager = new ChunkLoadingManager();
        register(chunkLoadingManager);


        PlayerAnimationServiceImpl playerAnimationService = new PlayerAnimationServiceImpl();
        register(playerAnimationService);

        PlayerServiceImpl playerService = new PlayerServiceImpl(
            playerAnimationService, inputService,
            playerProperties, worldService, inventoryService
        );
        register(playerService);


        AudioServiceImpl audioService = new AudioServiceImpl();
        register(audioService);


        ChatServiceImpl chatService = new ChatServiceImpl(
            playerService, multiplayerClient, commandService
        );
        register(chatService);

        GdxGame game = new GdxGame();
        register(game);


        ScreenManagerImpl screenManager = new ScreenManagerImpl(null, game);
        register(screenManager);

        log.debug("Bean registration completed successfully");
    }

    public static void dispose() {
        if (fullyInitialized) {
            try {
                log.info("Disposing Android game context");


                if (beans.containsKey(UiService.class)) {
                    getBean(UiService.class).dispose();
                }

                if (beans.containsKey(ObjectTextureManager.class)) {
                    getBean(ObjectTextureManager.class).disposeTextures();
                }

                if (beans.containsKey(AudioService.class)) {
                    getBean(AudioService.class).dispose();
                }

                if (beans.containsKey(ChunkLoadingManager.class)) {
                    getBean(ChunkLoadingManager.class).dispose();
                }

                beans.clear();
                fullyInitialized = false;
                log.info("Android game context disposed successfully");
            } catch (Exception e) {
                log.error("Error disposing Android game context", e);
            }
        }
    }

    private static class AndroidApplicationContext implements ApplicationContext {
        private final ApplicationEventPublisher eventPublisher;

        public AndroidApplicationContext(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            eventPublisher.publishEvent(event);
        }

        @Override
        public void publishEvent(Object event) {
            eventPublisher.publishEvent(event);
        }

        @Override
        public String getId() {
            return "androidApplicationContext";
        }

        @Override
        public String getApplicationName() {
            return "AndroidGame";
        }

        @Override
        public String getDisplayName() {
            return getApplicationName();
        }

        @Override
        public long getStartupDate() {
            return System.currentTimeMillis();
        }

        @Override
        public ApplicationContext getParent() {
            return null;
        }

        @Override
        public AutowireCapableBeanFactory getAutowireCapableBeanFactory() {
            throw new UnsupportedOperationException();
        }


        @Override
        public Environment getEnvironment() {
            return new StandardEnvironment();
        }


        @Override
        public boolean containsBean(String name) {
            return false;
        }

        @Override
        public Object getBean(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getBean(String name, Class<T> requiredType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getBean(String name, Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getBean(Class<T> requiredType) {
            return AndroidGameContext.getBean(requiredType);
        }

        @Override
        public <T> T getBean(Class<T> requiredType, Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsBeanDefinition(String beanName) {
            return false;
        }

        @Override
        public int getBeanDefinitionCount() {
            return 0;
        }

        @Override
        public String[] getBeanDefinitionNames() {
            return new String[0];
        }

        @Override
        public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
            return null;
        }

        @Override
        public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
            return null;
        }

        @Override
        public String[] getBeanNamesForType(ResolvableType type) {
            return new String[0];
        }

        @Override
        public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
            return new String[0];
        }

        @Override
        public String[] getBeanNamesForType(Class<?> type) {
            return new String[0];
        }

        @Override
        public String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
            return new String[0];
        }

        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
            return Map.of();
        }

        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
            return Map.of();
        }

        @Override
        public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
            return new String[0];
        }

        @Override
        public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
            return Map.of();
        }

        @Override
        public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {
            return null;
        }

        @Override
        public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
            return null;
        }

        @Override
        public <A extends Annotation> Set<A> findAllAnnotationsOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
            return Set.of();
        }

        @Override
        public boolean isSingleton(String name) {
            return true;
        }

        @Override
        public boolean isPrototype(String name) {
            return false;
        }

        @Override
        public boolean isTypeMatch(String name, ResolvableType typeToMatch) {
            return false;
        }

        @Override
        public boolean isTypeMatch(String name, Class<?> typeToMatch) {
            return false;
        }

        @Override
        public Class<?> getType(String name) {
            return null;
        }

        @Override
        public Class<?> getType(String name, boolean allowFactoryBeanInit) {
            return null;
        }

        @Override
        public String[] getAliases(String name) {
            return new String[0];
        }


        @Override
        public BeanFactory getParentBeanFactory() {
            return null;
        }

        @Override
        public boolean containsLocalBean(String name) {
            return false;
        }

        @Override
        public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            return "";
        }

        @Override
        public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
            return "";
        }

        @Override
        public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
            return "";
        }

        @Override
        public Resource[] getResources(String locationPattern) throws IOException {
            return new Resource[0];
        }

        @Override
        public Resource getResource(String location) {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return null;
        }
    }

    private static class AndroidEventPublisher implements ApplicationEventPublisher {
        private final SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();

        @Override
        public void publishEvent(ApplicationEvent event) {
            eventMulticaster.multicastEvent(event);
        }

        @Override
        public void publishEvent(Object event) {
            if (event instanceof ApplicationEvent) {
                publishEvent((ApplicationEvent) event);
            } else {
                publishEvent(new PayloadApplicationEvent<>(this, event));
            }
        }
    }
}
