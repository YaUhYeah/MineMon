package io.github.minemon.world.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Json;
import io.github.minemon.core.service.FileAccessService;
import io.github.minemon.world.config.TileConfig;
import io.github.minemon.world.service.TileManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
@Slf4j
@Component
@Qualifier("clientTileManagerImpl")
public class ClientTileManagerImpl implements TileManager {

    private static final String DEFAULT_CONFIG_PATH = "config/tiles.json";
    private String tileConfigFile;

    private final HashMap<Integer, TileConfig.TileDefinition> tiles = new HashMap<>();
    private TextureAtlas atlas;
    private boolean initialized = false;
    private final FileAccessService fileAccessService;

    public ClientTileManagerImpl(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
        this.tileConfigFile = DEFAULT_CONFIG_PATH;
    }

    private boolean configLoaded = false;
    @Override
    public void initIfNeeded() {
        if (!initialized) {
            try {
                
                if (!configLoaded) {
                    loadConfig(tileConfigFile);
                    configLoaded = true;
                }

                
                initialized = true;
                log.info("TileManager (client) initialized.");
            } catch (Exception e) {
                log.error("Failed to initialize TileManager", e);
                throw new RuntimeException("TileManager initialization failed", e);
            }
        }
    }


    private void ensureAtlasLoaded() {
        if (atlas == null) {
            if (Gdx.graphics == null || Gdx.graphics.getGL20() == null) {
                throw new IllegalStateException("OpenGL context not ready");
            }
            try {
                atlas = new TextureAtlas(Gdx.files.internal("atlas/tiles-gfx-atlas.atlas"));
            } catch (Exception e) {
                log.error("Failed to load texture atlas", e);
                throw new RuntimeException("Failed to load texture atlas", e);
            }
        }
    }

    @Override
    public TextureRegion getRegionForTile(int tileId) {
        ensureAtlasLoaded(); 

        TileConfig.TileDefinition def = tiles.get(tileId);
        if (def == null) {
            TextureRegion unknown = atlas.findRegion("unknown");
            if (unknown == null) {
                log.warn("Unknown tile requested and no 'unknown' region found.");
            }
            return unknown;
        }
        TextureRegion region = atlas.findRegion(def.getTexture());
        if (region == null) {
            log.warn("No region found in atlas for tile texture: {}", def.getTexture());
            return atlas.findRegion("unknown");
        }
        return region;
    }
    private void loadDefaultConfig() {
        log.info("Loading default tile configuration");
        
        TileConfig.TileDefinition defaultTile = new TileConfig.TileDefinition();
        defaultTile.setId(0);
        defaultTile.setName("unknown");
        defaultTile.setTexture("unknown");
        defaultTile.setPassable(false);
        tiles.put(0, defaultTile);
    }
    private void loadConfig(String configPath) {
        try {
            if (!fileAccessService.exists(configPath)) {
                log.error("Tile config file not found at: {}", configPath);
                loadDefaultConfig();
                return;
            }

            String jsonContent = fileAccessService.readFile(configPath);
            parseConfig(jsonContent);

        } catch (Exception e) {
            log.error("Error loading tile config", e);
            loadDefaultConfig();
        }
    }private void parseConfig(String jsonContent) {
        try {
            Json json = new Json();
            TileConfig config = json.fromJson(TileConfig.class, jsonContent);

            tiles.clear();
            for (TileConfig.TileDefinition def : config.getTiles()) {
                tiles.put(def.getId(), def);
            }

            log.info("Successfully loaded {} tiles", tiles.size());

        } catch (Exception e) {
            log.error("Error parsing tile config", e);
            loadDefaultConfig();
        }
    }

    @Override
    public boolean isPassable(int tileId) {
        TileConfig.TileDefinition def = tiles.get(tileId);
        if (def == null) return false;
        return def.isPassable();
    }

    @Override
    public String getTileName(int tileId) {
        TileConfig.TileDefinition def = tiles.get(tileId);
        if (def == null) return "unknown";
        return def.getName();
    }
}
