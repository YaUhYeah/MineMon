package io.github.minemon.world.service.impl;

import com.badlogic.gdx.Gdx;
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
        } else {
            atlas = new TextureAtlas(Gdx.files.internal(ATLAS_PATH));
            log.info("ObjectTextureManager initialized with atlas: {}", ATLAS_PATH);
            initialized = true;
        }
    }


    public TextureRegion getTexture(String name) {
        if (!initialized || atlas == null) {
            log.warn("ObjectTextureManager not initialized or atlas not loaded. Name: {}", name);
            return null;
        }


        if (regionCache.containsKey(name)) {
            return regionCache.get(name);
        }

        TextureRegion region = atlas.findRegion(name);
        if (region == null) {
            log.warn("No region found for '{}', trying 'unknown' texture.", name);
            region = atlas.findRegion("unknown");
            if (region == null) {
                log.error("No 'unknown' region found in atlas. Unable to provide fallback.");
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
            log.info("ObjectTextureManager atlas disposed.");
        }
    }

}
