package io.github.minemon.ios;

import io.github.minemon.GdxGame;
import io.github.minemon.context.GameApplicationContext;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;


public class IOSLauncher extends IOSApplication.Delegate {
    @Override
    protected IOSApplication createApplication() {
        IOSApplicationConfiguration configuration = new IOSApplicationConfiguration();
        configuration.useAccelerometer = false;
        configuration.useCompass = false;
        configuration.useGL30 = false;
        configuration.orientationLandscape = true;
        configuration.orientationPortrait = false;
        
        // Initialize Spring context before creating game
        System.setProperty("spring.profiles.active", "ios");
        GameApplicationContext.initContext(false);
        GdxGame game = GameApplicationContext.getContext().getBean(GdxGame.class);
        
        return new IOSApplication(game, configuration);
    }

    public static void main(String[] argv) {
        try {
            NSAutoreleasePool pool = new NSAutoreleasePool();
            UIApplication.main(argv, null, IOSLauncher.class);
            pool.close();
        } catch (Exception e) {
            System.err.println("Failed to start iOS application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
