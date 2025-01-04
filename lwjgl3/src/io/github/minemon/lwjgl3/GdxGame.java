package io.github.minemon.lwjgl3;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.screen.ModeSelectionScreen;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.SettingsService;
import io.github.minemon.core.service.UiService;
import io.github.minemon.core.ui.HotbarUI;
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
        io.github.minemon.lwjgl3.PokemeetupApplication.initSpring();

        // 2) Now retrieve beans from the static context
        SettingsService settingsService =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(SettingsService.class);
        settingsService.initialize();

        UiService uiService =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(UiService.class);
        uiService.initialize();

        TileManager tileManager =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(TileManager.class);
        tileManager.initIfNeeded();

        WorldObjectManager worldObjectManager =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(WorldObjectManager.class);
        worldObjectManager.initialize();

        WorldService worldService =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(WorldService.class);
        worldService.initIfNeeded();

        ObjectTextureManager objectTextureManager =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(ObjectTextureManager.class);
        objectTextureManager.initializeIfNeeded();


        WorldRenderer worldRenderer =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(WorldRenderer.class);
        worldRenderer.initialize();

        PlayerAnimationService playerAnimationService =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(PlayerAnimationService.class);
        playerAnimationService.initAnimationsIfNeeded();

        AudioService audioService =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(AudioService.class);
        audioService.initAudio();

        BiomeService biomeService =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(BiomeService.class);
        biomeService.init();

        // Use vsync from settings
        Gdx.graphics.setVSync(settingsService.getVSync());

        // Finally, switch to your initial screen
        ScreenManager screenManager =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(ScreenManager.class);
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
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(UiService.class);
        uiService.dispose();

        ObjectTextureManager objectTextureManager =
            io.github.minemon.lwjgl3.PokemeetupApplication.getSpringContext().getBean(ObjectTextureManager.class);
        objectTextureManager.disposeTextures();

        super.dispose();
    }
}
