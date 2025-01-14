package io.github.minemon.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.minemon.GdxGame;
import io.github.minemon.context.GameApplicationContext;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;

        try {
            // Initialize Spring context
            GameApplicationContext.initContext(false);
            GdxGame game = GameApplicationContext.getContext().getBean(GdxGame.class);
            if (game == null) {
                throw new RuntimeException("Failed to get GdxGame bean from context");
            }

            // Configure and start LibGDX application
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("PokeMeetup");
            config.setWindowedMode(1280, 720);
            config.setForegroundFPS(60);
            config.useVsync(true);

            new Lwjgl3Application(game, config);
        } catch (Exception e) {
            System.err.println("Failed to start game: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
