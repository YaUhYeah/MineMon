package io.github.minemon.gwt;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import io.github.minemon.GdxGame;


public class GwtLauncher extends GwtApplication {
        @Override
        public GwtApplicationConfiguration getConfig () {
            GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
            cfg.padVertical = 0;
            cfg.padHorizontal = 0;
            cfg.antialiasing = true;
            cfg.useGL30 = false;  // WebGL 2.0 not widely supported yet
            cfg.alpha = true;
            cfg.useDebugGL = false;
            return cfg;
        }

        @Override
        public ApplicationListener createApplicationListener () {
            // Set web profile and initialize context
            System.setProperty("spring.profiles.active", "web");
            try {
                GameApplicationContext.initContext(true);  // true for web mode
                return GameApplicationContext.getContext().getBean(GdxGame.class);
            } catch (Exception e) {
                GWT.log("Failed to initialize game context", e);
                throw new RuntimeException("Game initialization failed", e);
            }
        }
}
