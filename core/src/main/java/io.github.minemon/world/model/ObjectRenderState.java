package io.github.minemon.world.model;

import com.badlogic.gdx.graphics.Color;
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

        
        objectCache.put(obj.getId(), obj);

        
        float fadeState = objectFadeStates.computeIfAbsent(obj.getId(), k -> 0f);

        
        if (fadeState < 1.0f) {
            fadeState = Math.min(1.0f, fadeState + (delta / FADE_DURATION));
            objectFadeStates.put(obj.getId(), fadeState);
        }

        
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, fadeState);

        float x = obj.getTileX() * 32;
        float y = obj.getTileY() * 32;
        int width = obj.getType().getWidthInTiles() * 32;
        int height = obj.getType().getHeightInTiles() * 32;



        batch.draw(texture, x, y, width, height);

        
        batch.setColor(c.r, c.g, c.b, 1f);
    }

    
    public void renderObject(
        SpriteBatch batch, WorldObject obj,
        TextureRegion texture, float delta,
        float drawX, float drawY,
        float drawWidth, float drawHeight
    ) {
        if (texture == null) return;

        
        objectCache.put(obj.getId(), obj);
        float fadeState = objectFadeStates.computeIfAbsent(obj.getId(), k -> 0f);
        if (fadeState < 1.0f) {
            fadeState = Math.min(1.0f, fadeState + (delta / FADE_DURATION));
            objectFadeStates.put(obj.getId(), fadeState);
        }

        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, fadeState);

        batch.draw(texture, drawX, drawY, drawWidth, drawHeight);

        batch.setColor(c.r, c.g, c.b, 1f);
    }

    
    public void clearInvisibleObjects(Rectangle viewBounds) {
        
        Rectangle expandedBounds = new Rectangle(
            viewBounds.x - 64,
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

    private WorldObject getObjectById(String id) {
        
        WorldObject cached = objectCache.get(id);
        if (cached != null) {
            return cached;
        }

        
        Rectangle searchBounds = calculateSearchBounds();
        List<WorldObject> visibleObjects = worldService.getVisibleObjects(searchBounds);

        for (WorldObject obj : visibleObjects) {
            if (obj.getId().equals(id)) {
                objectCache.put(id, obj);
                return obj;
            }
        }

        return null;
    }

    private Rectangle calculateSearchBounds() {
        float width = worldService.getCamera().viewportWidth * worldService.getCamera().zoom;
        float height = worldService.getCamera().viewportHeight * worldService.getCamera().zoom;

        return new Rectangle(
            worldService.getCamera().position.x - (width / 2) - 128,
            worldService.getCamera().position.y - (height / 2) - 128,
            width + 256,
            height + 256
        );
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
