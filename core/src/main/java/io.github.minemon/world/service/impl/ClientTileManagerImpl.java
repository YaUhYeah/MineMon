package io.github.minemon.world.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Json;
import io.github.minemon.core.service.FileAccessService;
import io.github.minemon.world.config.TileConfig;
import io.github.minemon.world.service.TileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
@Profile("client")
@Qualifier("clientTileManagerImpl")
public class ClientTileManagerImpl implements TileManager {
    private static final Logger logger = LoggerFactory.getLogger(ClientTileManagerImpl.class);

    @Value("${tiles.configPath:config/tiles.json}")
    private String tileConfigFile;

    private final HashMap<Integer, TileConfig.TileDefinition> tiles = new HashMap<>();
    private TextureAtlas atlas;
    private boolean initialized = false;
    private final FileAccessService fileAccessService;

    public ClientTileManagerImpl(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
    }

    @Override
    public void initIfNeeded() {
        if (!initialized) {
            loadConfig(tileConfigFile);
            atlas = new TextureAtlas(Gdx.files.internal("assets/atlas/tiles-gfx-atlas.atlas"));
            initialized = true;
            logger.info("TileManager (client) initialized.");
        }
    }

    private void loadConfig(String tileConfigFile) {
        if (!fileAccessService.exists(tileConfigFile)) {
            logger.warn("Tile config file not found: {}", tileConfigFile);
            return;
        }

        String jsonContent = fileAccessService.readFile(tileConfigFile);
        Json json = new Json();
        TileConfig config = json.fromJson(TileConfig.class, jsonContent);
        for (TileConfig.TileDefinition def : config.getTiles()) {
            tiles.put(def.getId(), def);
        }
        logger.info("Loaded {} tiles from {}", tiles.size(), tileConfigFile);
    }

    @Override
    public TextureRegion getRegionForTile(int tileId) {
        TileConfig.TileDefinition def = tiles.get(tileId);
        if (def == null) {
            TextureRegion unknown = atlas.findRegion("unknown");
            if (unknown == null) {
                logger.warn("Unknown tile requested and no 'unknown' region found.");
            }
            return unknown;
        }
        TextureRegion region = atlas.findRegion(def.getTexture());
        if (region == null) {
            logger.warn("No region found in atlas for tile texture: {}", def.getTexture());
            return atlas.findRegion("unknown");
        }
        return region;
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
