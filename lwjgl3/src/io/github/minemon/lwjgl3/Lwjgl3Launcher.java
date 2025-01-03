package io.github.minemon.lwjgl3;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        PokemeetupApplication.initSpring();
        GdxGame game = PokemeetupApplication.getSpringContext().getBean(GdxGame.class);

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("PokeMeetup");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(true);


        new Lwjgl3Application(game, config);
    }
}
