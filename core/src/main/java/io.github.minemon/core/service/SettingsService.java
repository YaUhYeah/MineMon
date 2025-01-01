package io.github.minemon.core.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import io.github.minemon.core.config.GameSettings;
import io.github.minemon.input.InputConfiguration;
import io.github.minemon.player.model.PlayerDirection;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SettingsService {
    private static final String PREFS_NAME = "pokemeetup_settings";
    private final InputConfiguration inputConfig;
    private final float DEFAULT_MUSIC_VOLUME = 0.7f;
    private final float DEFAULT_SOUND_VOLUME = 1.0f;
    private final boolean DEFAULT_VSYNC = true;
    private final int DEFAULT_RENDER_DISTANCE = 12;
    private final boolean DEFAULT_PARTICLES = true;
    private final boolean DEFAULT_SMOOTH_LIGHTING = true;
    private Preferences prefs;
    private GameSettings currentSettings;
    private boolean initialized = false;
    @Getter
    private Map<String, Integer> keyBindings;

    public SettingsService(InputConfiguration inputConfig) {
        this.inputConfig = inputConfig;
        this.keyBindings = new HashMap<>();
        initializeDefaultKeyBindings();
        createDefaultSettings();
    }

    private void initializeDefaultKeyBindings() {
        keyBindings.put("UP", Input.Keys.W);
        keyBindings.put("DOWN", Input.Keys.S);
        keyBindings.put("LEFT", Input.Keys.A);
        keyBindings.put("RIGHT", Input.Keys.D);
        keyBindings.put("RUN", Input.Keys.Z);
        updateInputConfiguration();
    }

    private void createDefaultSettings() {
        currentSettings = new GameSettings();
        currentSettings.setMusicVolume(DEFAULT_MUSIC_VOLUME);
        currentSettings.setSoundVolume(DEFAULT_SOUND_VOLUME);
        currentSettings.setVSync(DEFAULT_VSYNC);
        currentSettings.setRenderDistance(DEFAULT_RENDER_DISTANCE);
        currentSettings.setParticles(DEFAULT_PARTICLES);
        currentSettings.setSmoothLighting(DEFAULT_SMOOTH_LIGHTING);
    }

    public void initialize() {
        if (initialized || Gdx.app == null) return;

        prefs = Gdx.app.getPreferences(PREFS_NAME);
        loadAllSettings();
        initialized = true;
    }

    private void loadAllSettings() {

        currentSettings.setMusicVolume(prefs.getFloat("music_volume", DEFAULT_MUSIC_VOLUME));
        currentSettings.setSoundVolume(prefs.getFloat("sound_volume", DEFAULT_SOUND_VOLUME));
        currentSettings.setVSync(prefs.getBoolean("vsync", DEFAULT_VSYNC));
        currentSettings.setRenderDistance(prefs.getInteger("render_distance", DEFAULT_RENDER_DISTANCE));
        currentSettings.setParticles(prefs.getBoolean("particles", DEFAULT_PARTICLES));
        currentSettings.setSmoothLighting(prefs.getBoolean("smooth_lighting", DEFAULT_SMOOTH_LIGHTING));


        keyBindings.put("UP", prefs.getInteger("key_up", Input.Keys.W));
        keyBindings.put("DOWN", prefs.getInteger("key_down", Input.Keys.S));
        keyBindings.put("LEFT", prefs.getInteger("key_left", Input.Keys.A));
        keyBindings.put("RIGHT", prefs.getInteger("key_right", Input.Keys.D));
        keyBindings.put("RUN", prefs.getInteger("key_run", Input.Keys.Z));

        updateInputConfiguration();
    }

    private void updateInputConfiguration() {
        Map<Integer, PlayerDirection> newMovementKeys = new HashMap<>();
        newMovementKeys.put(keyBindings.get("UP"), PlayerDirection.UP);
        newMovementKeys.put(keyBindings.get("DOWN"), PlayerDirection.DOWN);
        newMovementKeys.put(keyBindings.get("LEFT"), PlayerDirection.LEFT);
        newMovementKeys.put(keyBindings.get("RIGHT"), PlayerDirection.RIGHT);
        inputConfig.setMovementKeys(newMovementKeys);
        inputConfig.setRunKey(keyBindings.get("RUN"));
    }

    public GameSettings getSettings() {
        return currentSettings;
    }

    public void updateMusicVolume(float volume) {
        if (initialized && prefs != null) {
            prefs.putFloat("music_volume", volume);
            prefs.flush();
        }
        currentSettings.setMusicVolume(volume);
    }

    public void updateSoundVolume(float volume) {
        if (initialized && prefs != null) {
            prefs.putFloat("sound_volume", volume);
            prefs.flush();
        }
        currentSettings.setSoundVolume(volume);
    }

    public void updateVSync(boolean enabled) {
        if (initialized && prefs != null) {
            prefs.putBoolean("vsync", enabled);
            prefs.flush();
            Gdx.graphics.setVSync(enabled);
        }
        currentSettings.setVSync(enabled);
    }

    public void updateRenderDistance(int distance) {
        if (initialized && prefs != null) {
            prefs.putInteger("render_distance", distance);
            prefs.flush();
        }
        currentSettings.setRenderDistance(distance);
    }

    public void updateParticles(boolean enabled) {
        if (initialized && prefs != null) {
            prefs.putBoolean("particles", enabled);
            prefs.flush();
        }
        currentSettings.setParticles(enabled);
    }

    public void updateSmoothLighting(boolean enabled) {
        if (initialized && prefs != null) {
            prefs.putBoolean("smooth_lighting", enabled);
            prefs.flush();
        }
        currentSettings.setSmoothLighting(enabled);
    }

    public void setKeyBinding(String action, int keycode) {
        keyBindings.put(action, keycode);
        if (initialized && prefs != null) {
            prefs.putInteger("key_" + action.toLowerCase(), keycode);
            prefs.flush();
        }
        updateInputConfiguration();
    }

    public float getMusicVolume() {
        if (initialized && prefs != null) {
            return prefs.getFloat("music_volume", currentSettings.getMusicVolume());
        }
        return currentSettings.getMusicVolume();
    }

    public void setMusicVolume(float volume) {
        if (initialized && prefs != null) {
            prefs.putFloat("music_volume", volume);
            prefs.flush();
        }
        currentSettings.setMusicVolume(volume);
    }

    public float getSoundVolume() {
        if (initialized && prefs != null) {
            return prefs.getFloat("sound_volume", currentSettings.getSoundVolume());
        }
        return currentSettings.getSoundVolume();
    }

    public void setSoundVolume(float volume) {
        if (initialized && prefs != null) {
            prefs.putFloat("sound_volume", volume);
            prefs.flush();
        }
        currentSettings.setSoundVolume(volume);
    }

    public boolean getVSync() {
        if (initialized && prefs != null) {
            return prefs.getBoolean("vsync", currentSettings.isVSync());
        }
        return currentSettings.isVSync();
    }

    public void setVSync(boolean enabled) {
        if (initialized && prefs != null) {
            prefs.putBoolean("vsync", enabled);
            prefs.flush();
            Gdx.graphics.setVSync(enabled);
        }
        currentSettings.setVSync(enabled);
    }
}