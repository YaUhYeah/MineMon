package io.github.minemon.inventory.service.impl;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.minemon.inventory.config.ItemRegistry;
import io.github.minemon.inventory.model.InventoryItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ItemTextureManager {
    private TextureAtlas atlas;
    private final Map<String, TextureRegion> regionCache = new HashMap<>();
    private boolean initialized = false;

    public void initialize(TextureAtlas itemAtlas) {
        if (!initialized) {
            this.atlas = itemAtlas;
            initialized = true;
            loadTextures();
            log.info("ItemTextureManager initialized with {} textures", regionCache.size());
        }
    }

    private void loadTextures() {
        for (InventoryItem item : ItemRegistry.getDefaultItems()) {
            TextureRegion region = findTextureForItem(item.getItemId());
            if (region != null) {
                regionCache.put(item.getItemId(), region);
                log.debug("Loaded texture for item: {}", item.getItemId());
            }
        }
    }

    public TextureRegion getTexture(String itemId) {
        if (!initialized) {
            log.error("Attempted to get texture before initialization");
            return null;
        }

        TextureRegion region = regionCache.get(itemId);
        if (region == null) {
            region = findTextureForItem(itemId);
            if (region != null) {
                regionCache.put(itemId, region);
            }
        }
        return region;
    }

    private TextureRegion findTextureForItem(String itemId) {
        String[] attempts = {
            itemId + "_item",
            itemId,
            itemId.toLowerCase() + "_item",
            itemId.toLowerCase(),
            "missing_texture" // Fallback texture
        };

        for (String key : attempts) {
            TextureRegion region = atlas.findRegion(key);
            if (region != null) {
                return region;
            }
        }

        log.warn("No texture found for item: {}", itemId);
        return atlas.findRegion("missing_texture");
    }

    public void dispose() {
        regionCache.clear();
        initialized = false;
    }
}
