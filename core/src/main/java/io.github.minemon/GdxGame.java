package io.github.minemon;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.context.GameApplicationContext;
import io.github.minemon.context.AndroidGameContext;
import io.github.minemon.core.screen.ModeSelectionScreen;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import io.github.minemon.core.service.UiService;
import io.github.minemon.core.ui.AndroidUIFactory;
import io.github.minemon.input.AndroidTouchInput;
import io.github.minemon.input.InputService;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.service.TileManager;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import io.github.minemon.world.service.impl.ObjectTextureManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class GdxGame extends Game {
    private final boolean isAndroid;

    public GdxGame() {

        this.isAndroid = isAndroidPlatform();
    }

    private boolean isAndroidPlatform() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void create() {
        try {
            String platform = getPlatformName();
            log.info("GdxGame.create() called on platform: {}", platform);

            // Wait for graphics context with timeout
            if (!waitForGraphicsContext()) {
                throw new RuntimeException("Graphics context initialization timeout");
            }

            // Get appropriate application context
            ApplicationContext context = getApplicationContext();
            initializeServices(context);

            // Show initial screen safely
            showInitialScreen(context);

            log.info("Game initialization completed successfully on {}", platform);

        } catch (Exception e) {
            log.error("Failed to initialize game: {}", e.getMessage(), e);
            throw new RuntimeException("Game initialization failed", e);
        }
    }

    private String getPlatformName() {
        if (isAndroid) return "Android";
        if (Gdx.app.getType() == Application.ApplicationType.iOS) return "iOS";
        if (Gdx.app.getType() == Application.ApplicationType.WebGL) return "WebGL";
        return "Desktop";
    }

    private boolean waitForGraphicsContext() {
        int attempts = 0;
        while (Gdx.gl == null && attempts < 20) {  // Increased timeout
            try {
                Thread.sleep(100);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return Gdx.app != null && Gdx.graphics != null && Gdx.gl != null;
    }

    private ApplicationContext getApplicationContext() {
        ApplicationContext context;
        if (isAndroid) {
            if (!AndroidGameContext.isRegistered(ApplicationContext.class)) {
                throw new RuntimeException("Android context not initialized");
            }
            context = AndroidGameContext.getBean(ApplicationContext.class);
        } else {
            context = GameApplicationContext.getContext();
        }
        if (context == null) {
            throw new RuntimeException("Application context is null");
        }
        return context;
    }

    private void initializeServices(ApplicationContext context) {
        // Core services
        initializeService("SettingsService", () -> 
            context.getBean(SettingsService.class).initialize());
        
        initializeService("UiService", () -> 
            context.getBean(UiService.class).initialize());

        // Platform-specific initialization
        if (isAndroid) {
            initializeAndroidServices(context);
        }

        // World services
        log.info("Initializing world services...");
        initializeService("TileManager", () -> 
            context.getBean(TileManager.class).initIfNeeded());
        initializeService("WorldObjectManager", () -> 
            context.getBean(WorldObjectManager.class).initialize());
        initializeService("WorldService", () -> 
            context.getBean(WorldService.class).initIfNeeded());
        initializeService("ObjectTextureManager", () -> 
            context.getBean(ObjectTextureManager.class).initializeIfNeeded());

        // Game services
        log.info("Initializing game services...");
        initializeService("PlayerAnimationService", () -> 
            context.getBean(PlayerAnimationService.class).initAnimationsIfNeeded());
        initializeService("AudioService", () -> 
            context.getBean(AudioService.class).initAudio());
        initializeService("BiomeService", () -> 
            context.getBean(BiomeService.class).init());
    }

    private void initializeService(String serviceName, Runnable initializer) {
        try {
            initializer.run();
        } catch (Exception e) {
            log.error("Failed to initialize {}: {}", serviceName, e.getMessage());
            if (isRequiredService(serviceName)) {
                throw e;
            }
        }
    }

    private boolean isRequiredService(String serviceName) {
        return serviceName.equals("SettingsService") || 
               serviceName.equals("UiService") ||
               serviceName.equals("WorldService") ||
               serviceName.equals("TileManager");
    }

    private void initializeAndroidServices(ApplicationContext context) {
        try {
            AndroidTouchInput touchInput = context.getBean(AndroidTouchInput.class);
            touchInput.initialize(AndroidUIFactory.createTouchpadStyle());
        } catch (Exception e) {
            log.error("Failed to initialize Android touch input", e);
        }
    }

    private void showInitialScreen(ApplicationContext context) {
        ScreenManager screenManager = context.getBean(ScreenManager.class);
        if (screenManager == null) {
            throw new RuntimeException("Screen manager is null");
        }

        Gdx.app.postRunnable(() -> {
            try {
                screenManager.showScreen(ModeSelectionScreen.class);
            } catch (Exception e) {
                log.error("Failed to show initial screen", e);
                throw new RuntimeException("Failed to show initial screen", e);
            }
        });
    }

    private AndroidTouchInput touchInput;
    private boolean touchInputInitialized = false;

    @Override
    public void render() {
        super.render();

        // Update and render Android touch controls if needed
        if (isAndroid) {
            try {
                if (!touchInputInitialized) {
                    try {
                        touchInput = AndroidGameContext.getBean(AndroidTouchInput.class);
                    } catch (Exception e) {
                        // Fallback to getInstance if bean lookup fails
                        InputService inputService = AndroidGameContext.getBean(InputService.class);
                        touchInput = AndroidTouchInput.getInstance(inputService);
                    }
                    touchInputInitialized = true;
                }

                if (touchInput != null) {
                    touchInput.update();
                    touchInput.render();
                }
            } catch (Exception e) {
                // Only log once to avoid spam
                if (!touchInputInitialized) {
                    log.error("Error updating Android touch controls", e);
                }
            }
        }
    }

    @Override
    public void dispose() {
        if (touchInput != null) {
            touchInput.dispose();
            touchInput = null;
        }

        if (!isAndroid) {
            ApplicationContext context = isAndroid ? AndroidGameContext.getBean(ApplicationContext.class) : GameApplicationContext.getContext();
            if (context != null) {
                context.getBean(UiService.class).dispose();
                context.getBean(ObjectTextureManager.class).disposeTextures();
            }
        }
        super.dispose();
    }
}
