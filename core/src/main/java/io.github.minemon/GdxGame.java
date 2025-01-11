package io.github.minemon;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.context.GameApplicationContext;
import io.github.minemon.core.screen.ModeSelectionScreen;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import io.github.minemon.core.service.UiService;
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
            log.info("GdxGame.create() called on platform: {}", isAndroid ? "Android" : "Desktop");

            ApplicationContext context = GameApplicationContext.getContext();
            if (context == null) {
                throw new RuntimeException("Application context is null");
            }

            // Initialize core services
            log.info("Initializing core services...");
            context.getBean(SettingsService.class).initialize();
            context.getBean(UiService.class).initialize();
            
            // Initialize Android-specific UI if needed
            if (isAndroid) {
                AndroidTouchInput touchInput = context.getBean(AndroidTouchInput.class);
                touchInput.initialize(AndroidUIFactory.createTouchpadStyle());
            }

            log.info("Initializing world services...");
            context.getBean(TileManager.class).initIfNeeded();
            context.getBean(WorldObjectManager.class).initialize();
            context.getBean(WorldService.class).initIfNeeded();
            context.getBean(ObjectTextureManager.class).initializeIfNeeded();

            log.info("Initializing game services...");
            context.getBean(PlayerAnimationService.class).initAnimationsIfNeeded();
            context.getBean(AudioService.class).initAudio();
            context.getBean(BiomeService.class).init();

            
            log.info("Showing initial screen...");
            ScreenManager screenManager = context.getBean(ScreenManager.class);

            if (screenManager == null) {
                throw new RuntimeException("Screen manager is null");
            }
            Gdx.app.postRunnable(() -> {
                try {
                    screenManager.showScreen(ModeSelectionScreen.class);
                } catch (Exception e) {
                    log.error("Failed to show initial screen", e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to initialize game: {}", e.getMessage(), e);
            throw new RuntimeException("Game initialization failed", e);
        }
    }

    @Override
    public void render() {
        super.render();
        
        // Update and render Android touch controls if needed
        if (isAndroid) {
            try {
                AndroidTouchInput touchInput = GameApplicationContext.getBean(AndroidTouchInput.class);
                touchInput.update();
                touchInput.render();
            } catch (Exception e) {
                log.error("Error updating Android touch controls", e);
            }
        }
    }

    @Override
    public void dispose() {
        
        if (!isAndroid) {
            ApplicationContext context = GameApplicationContext.getContext();
            context.getBean(UiService.class).dispose();
            context.getBean(ObjectTextureManager.class).disposeTextures();
        }
        super.dispose();
    }
}
