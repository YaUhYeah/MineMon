package io.github.minemon.core.service.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AndroidAssetManager {
    private boolean initialized = false;

    public void initialize() {
        if (initialized) return;

        try {
            verifyRequiredAssets();
            initialized = true;
        } catch (Exception e) {
            log.error("Failed to initialize asset manager", e);
            throw new RuntimeException("Asset initialization failed", e);
        }
    }

    private void verifyRequiredAssets() {
        
        String[] requiredAssets = {
            "Skins/uiskin.json",
            "atlas/tiles-gfx-atlas.atlas",
            "atlas/ui-gfx-atlas.atlas",
            "atlas/boy-gfx-atlas.atlas",
        };

        for (String asset : requiredAssets) {
            FileHandle file = Gdx.files.internal(asset);
            if (!file.exists()) {
                log.error("Required asset not found: {}", asset);
                log.error("Current path: {}", file.path());
                throw new RuntimeException("Missing required asset: " + asset);
            }
        }
    }
}
