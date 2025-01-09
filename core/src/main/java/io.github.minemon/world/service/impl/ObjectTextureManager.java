package io.github.minemon.world.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ObjectTextureManager {
    private TextureAtlas atlas;
    private final Map<String, TextureRegion> regionCache = new HashMap<>();
    private boolean initialized = false;

    public void initializeIfNeeded() {
        if (initialized) return;

        if (Gdx.files == null) {
            log.warn("Gdx environment not ready. Cannot load atlas yet.");
            return;
        }

        String ATLAS_PATH = "atlas/tiles-gfx-atlas.atlas";
        if (!Gdx.files.internal(ATLAS_PATH).exists()) {
            log.error("Atlas file not found at: {}", ATLAS_PATH);
            return;
        }

        atlas = new TextureAtlas(Gdx.files.internal(ATLAS_PATH));

        
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            region.getTexture().setFilter(
                com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
            );region.getTexture().setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        }

        
        verifyTreeTextures();

        initialized = true;
        log.info("ObjectTextureManager initialized successfully");
    }


    private void verifyTreeTextures() {
        String[] treeTypes = {
            "treeONE", "treeTWO", "snow_tree", "haunted_tree",
            "ruins_tree", "apricorn_tree_grown", "rain_tree", "CherryTree"
        };

        for (String treeName : treeTypes) {
            TextureRegion region = atlas.findRegion(treeName);
            if (region == null) {
                log.error("Failed to load tree texture: {}", treeName);
            } else {
                regionCache.put(treeName, region);
                log.debug("Successfully loaded tree texture: {}", treeName);
            }
        }
    }

    public TextureRegion getTexture(String name) {
        if (!initialized) {
            initializeIfNeeded();
        }

        if (name == null) {
            log.warn("Null texture name requested");
            return null;
        }

        
        TextureRegion cached = regionCache.get(name);
        if (cached != null) {
            return cached;
        }

        
        TextureRegion region = atlas.findRegion(name);
        if (region == null) {
            log.warn("No region found for '{}', trying 'unknown' texture", name);
            region = atlas.findRegion("unknown");
            if (region == null) {
                log.error("No 'unknown' region found in atlas either!");
                return null;
            }
        }

        
        regionCache.put(name, region);
        return region;
    }

    public void disposeTextures() {
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
            log.info("Disposed ObjectTextureManager atlas");
        }
        regionCache.clear();
        initialized = false;
    }
}
