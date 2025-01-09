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
            return cfg;
            
            
            
        }

        @Override
        public ApplicationListener createApplicationListener () {
            return new GdxGame();
        }
}
