package io.github.minemon.core.service.impl;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import io.github.minemon.core.screen.ModeSelectionScreen;
import io.github.minemon.core.service.ScreenManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

@Service
@Primary
@Slf4j
public class ScreenManagerImpl implements ScreenManager {
    private final ApplicationContext applicationContext;
    private final Game game;
    private final Stack<Class<? extends Screen>> screenHistory = new Stack<>();
    private Screen previousScreen;
    private final Map<Class<? extends Screen>, Screen> screenCache = new HashMap<>();
    private boolean transitioning = false;

    @Autowired
    public ScreenManagerImpl(ApplicationContext applicationContext, Game game) {
        this.applicationContext = applicationContext;
        this.game = game;
        log.info("ScreenManagerImpl constructor: game instance = {}, hash={}",
            game, System.identityHashCode(game));
    }

    @Override
    public void disposeScreen(Class<? extends Screen> screenClass) {
        try {
            Screen screen = screenCache.remove(screenClass);
            if (screen != null) {
                try {
                    screen.hide();
                    screen.dispose();
                    log.debug("Disposed screen: {}", screenClass.getSimpleName());
                } catch (Exception e) {
                    log.error("Error disposing screen {}: {}", screenClass.getSimpleName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in disposeScreen: {}", e.getMessage(), e);
        }
    }
    @Override
    public <T extends Screen> T getScreen(Class<T> screenClass) {
        try {
            log.info("Getting screen for class: {}", screenClass.getSimpleName());

            
            @SuppressWarnings("unchecked")
            T cachedScreen = (T) screenCache.get(screenClass);
            if (cachedScreen != null) {
                log.debug("Returning cached screen: {}", screenClass.getSimpleName());
                return cachedScreen;
            }

            
            T newScreen = applicationContext.getBean(screenClass);
            screenCache.put(screenClass, newScreen);
            log.info("Created new screen instance: {}", screenClass.getSimpleName());
            return newScreen;
        } catch (Exception e) {
            log.error("Error getting screen {}: {}", screenClass.getSimpleName(), e.getMessage(), e);
        }
        return null;
    }

    @PreDestroy
    public void dispose() {
        for (Screen screen : screenCache.values()) {
            if (screen != null) {
                try {
                    screen.dispose();
                } catch (Exception e) {
                    log.error("Error disposing screen: {}", e.getMessage());
                }
            }
        }
        screenCache.clear();
        screenHistory.clear();
    }
    @Override
    public void showScreen(Class<? extends Screen> screenClass) {
        if (transitioning) {
            log.warn("Screen transition already in progress, ignoring request");
            return;
        }

        transitioning = true;
        try {
            log.info("Attempting to show screen: {}", screenClass.getSimpleName());

            Screen newScreen = getScreen(screenClass);
            if (newScreen == null) {
                log.error("Failed to get screen instance for: {}", screenClass.getSimpleName());
                transitioning = false;
                return;
            }

            screenHistory.push(screenClass);
            previousScreen = game.getScreen();

            
            if (previousScreen != null) {
                log.debug("Hiding previous screen");
                previousScreen.hide();
            }

            
            Gdx.app.postRunnable(() -> {
                try {
                    log.info("Setting new screen: {}", screenClass.getSimpleName());
                    game.setScreen(newScreen);
                    log.info("Screen set successfully");
                } catch (Exception e) {
                    log.error("Error setting screen {}: {}", screenClass.getSimpleName(), e.getMessage(), e);
                } finally {
                    transitioning = false;
                }
            });
        } catch (Exception e) {
            log.error("Error during screen transition: {}", e.getMessage(), e);
            transitioning = false;
        }
    }

    @Override
    public void goBack() {
        if (transitioning) {
            log.warn("Screen transition already in progress, ignoring back request");
            return;
        }

        if (screenHistory.size() <= 1) {
            log.warn("Cannot go back, no previous screen in history");
            return;
        }

        transitioning = true;
        try {
            screenHistory.pop(); 
            Class<? extends Screen> previous = screenHistory.peek();
            Screen currentScreen = game.getScreen();

            if (currentScreen != null) {
                currentScreen.hide();
            }

            Screen newScreen = getScreen(previous);
            previousScreen = currentScreen;

            Gdx.app.postRunnable(() -> {
                try {
                    game.setScreen(newScreen);
                    log.debug("Went back to screen: {}", previous.getSimpleName());
                } catch (Exception e) {
                    log.error("Error during back navigation: {}", e.getMessage());
                } finally {
                    transitioning = false;
                }
            });
        } catch (Exception e) {
            log.error("Error during back navigation: {}", e.getMessage());
            transitioning = false;
        }
    }

    @Override
    public Screen getPreviousScreen() {
        return previousScreen;
    }
}
