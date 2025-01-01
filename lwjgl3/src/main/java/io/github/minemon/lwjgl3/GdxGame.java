package io.github.minemon.lwjgl3;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.screen.ModeSelectionScreen;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import io.github.minemon.core.service.UiService;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.world.biome.service.BiomeService;
import io.github.minemon.world.model.WorldRenderer;
import io.github.minemon.world.service.TileManager;
import io.github.minemon.world.service.WorldObjectManager;
import io.github.minemon.world.service.WorldService;
import io.github.minemon.world.service.impl.ObjectTextureManager;
import org.springframework.stereotype.Component;

@Component
public class GdxGame extends Game {

    @Override
    public void create() {
        // 1) Start Spring if not started yet
        PokemeetupApplication.initSpring();

        // 2) Now retrieve beans from the static context
        SettingsService settingsService =
            PokemeetupApplication.getSpringContext().getBean(SettingsService.class);
        settingsService.initialize();

        UiService uiService =
            PokemeetupApplication.getSpringContext().getBean(UiService.class);
        uiService.initialize();

        TileManager tileManager =
            PokemeetupApplication.getSpringContext().getBean(TileManager.class);
        tileManager.initIfNeeded();

        WorldObjectManager worldObjectManager =
            PokemeetupApplication.getSpringContext().getBean(WorldObjectManager.class);
        worldObjectManager.initialize();

        WorldService worldService =
            PokemeetupApplication.getSpringContext().getBean(WorldService.class);
        worldService.initIfNeeded();

        ObjectTextureManager objectTextureManager =
            PokemeetupApplication.getSpringContext().getBean(ObjectTextureManager.class);
        objectTextureManager.initializeIfNeeded();

        WorldRenderer worldRenderer =
            PokemeetupApplication.getSpringContext().getBean(WorldRenderer.class);
        worldRenderer.initialize();

        PlayerAnimationService playerAnimationService =
            PokemeetupApplication.getSpringContext().getBean(PlayerAnimationService.class);
        playerAnimationService.initAnimationsIfNeeded();

        AudioService audioService =
            PokemeetupApplication.getSpringContext().getBean(AudioService.class);
        audioService.initAudio();

        BiomeService biomeService =
            PokemeetupApplication.getSpringContext().getBean(BiomeService.class);
        biomeService.init();

        // Use vsync from settings
        Gdx.graphics.setVSync(settingsService.getVSync());

        // Finally, switch to your initial screen
        ScreenManager screenManager =
            PokemeetupApplication.getSpringContext().getBean(ScreenManager.class);
        screenManager.showScreen(ModeSelectionScreen.class);
    }

    @Override
    public void render() {
        super.render();
    }

    @Override
    public void dispose() {
        // Clean up resources from your beans
        UiService uiService =
            PokemeetupApplication.getSpringContext().getBean(UiService.class);
        uiService.dispose();

        ObjectTextureManager objectTextureManager =
            PokemeetupApplication.getSpringContext().getBean(ObjectTextureManager.class);
        objectTextureManager.disposeTextures();

        super.dispose();
    }
}
