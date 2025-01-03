package io.github.minemon.world.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ObjectRenderState {
    private static final float FADE_DURATION = 0.5f;
    private final Map<String, Float> objectFadeStates = new ConcurrentHashMap<>();
    private final Map<String, WorldObject> objectCache = new ConcurrentHashMap<>();

    @Autowired
    private WorldService worldService;

    public void renderObject(SpriteBatch batch, WorldObject obj, TextureRegion texture, float delta) {
        if (texture == null) return;

        // Cache the object for later lookup
        objectCache.put(obj.getId(), obj);

        // Get or initialize fade state
        float fadeState = objectFadeStates.computeIfAbsent(obj.getId(), k -> 0f);

        // Update fade state
        if (fadeState < 1.0f) {
            fadeState = Math.min(1.0f, fadeState + (delta / FADE_DURATION));
            objectFadeStates.put(obj.getId(), fadeState);
        }

        // Apply fade state
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, fadeState);

        float x = obj.getTileX() * 32;
        float y = obj.getTileY() * 32;
        int width = obj.getType().getWidthInTiles() * 32;
        int height = obj.getType().getHeightInTiles() * 32;

        // Special handling for trees to center them
        if (isTreeType(obj.getType())) {
            x -= 32; // Center the tree on the tile
        }

        batch.draw(texture, x, y, width, height);
        batch.setColor(c.r, c.g, c.b, 1f);
    }

    public void clearInvisibleObjects(Rectangle viewBounds) {
        // Expand view bounds slightly to prevent premature clearing
        Rectangle expandedBounds = new Rectangle(
            viewBounds.x - 64, // Two tiles extra buffer
            viewBounds.y - 64,
            viewBounds.width + 128,
            viewBounds.height + 128
        );

        objectFadeStates.keySet().removeIf(id -> {
            WorldObject obj = getObjectById(id);
            if (obj == null) {
                objectCache.remove(id);
                return true;
            }

            float objX = obj.getTileX() * 32;
            float objY = obj.getTileY() * 32;

            boolean visible = expandedBounds.contains(objX, objY);
            if (!visible) {
                objectCache.remove(id);
                return true;
            }
            return false;
        });
    }

    private Rectangle calculateSearchBounds(OrthographicCamera camera) {
        float width = camera.viewportWidth * camera.zoom;
        float height = camera.viewportHeight * camera.zoom;

        // Add extra padding to ensure we catch all relevant objects
        float padding = 128f; // 4 tiles worth of padding

        return new Rectangle(
            camera.position.x - (width / 2) - padding,
            camera.position.y - (height / 2) - padding,
            width + (padding * 2),
            height + (padding * 2)
        );
    }

    // Then update the getObjectById method to use this:
    private WorldObject getObjectById(String id) {
        // First check our cache
        WorldObject cached = objectCache.get(id);
        if (cached != null) {
            return cached;
        }

        // If not in cache, search through all visible objects using proper bounds
        Rectangle searchBounds = calculateSearchBounds(worldService.getCamera());
        List<WorldObject> visibleObjects = worldService.getVisibleObjects(searchBounds);

        for (WorldObject obj : visibleObjects) {
            if (obj.getId().equals(id)) {
                objectCache.put(id, obj);
                return obj;
            }
        }

        return null;
    }

    private boolean isTreeType(ObjectType type) {
        return type == ObjectType.TREE_0 ||
            type == ObjectType.TREE_1 ||
            type == ObjectType.SNOW_TREE ||
            type == ObjectType.HAUNTED_TREE ||
            type == ObjectType.RUINS_TREE ||
            type == ObjectType.APRICORN_TREE ||
            type == ObjectType.RAIN_TREE ||
            type == ObjectType.CHERRY_TREE;
    }

    public void reset() {
        objectFadeStates.clear();
        objectCache.clear();
    }

    public boolean hasObject(String id) {
        return objectCache.containsKey(id) || objectFadeStates.containsKey(id);
    }

    public float getFadeState(String id) {
        return objectFadeStates.getOrDefault(id, 0f);
    }
}
