package io.github.minemon.world.model;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.world.service.TileManager;
import io.github.minemon.world.service.WorldService;
import io.github.minemon.world.service.impl.ObjectTextureManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class WorldRenderer {
    private static final int TILE_SIZE = 32;
    private static final int CHUNK_SIZE = 16;

    private static final int VIEW_PADDING = 5;
    private static final Color VOID_COLOR = new Color(0.1f, 0.1f, 0.1f, 1f); // Dark gray for unloaded chunks
    private final TileManager tileManager;
    private final WorldService worldService;
    private final ObjectTextureManager objectTextureManager;
    private SpriteBatch batch;
    private boolean initialized = false;
    private float currentDelta = 0f;
    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;

    public WorldRenderer(WorldService worldService, TileManager tileManager, ObjectTextureManager objectTextureManager) {
        this.worldService = worldService;
        this.tileManager = tileManager;
        this.objectTextureManager = objectTextureManager;
    }

    public void initialize() {
        if (!initialized) {
            this.batch = new SpriteBatch();
            initialized = true;
        }
    }

    public void render(OrthographicCamera camera, float delta) {
        if (!initialized) {
            initialize();
        }

        Rectangle viewBounds = calculateViewBounds();
        Map<String, ChunkData> visibleChunks = worldService.getVisibleChunks(viewBounds);

        log.debug("Rendering {} chunks in viewport", visibleChunks.size());

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Render ground layer first
        renderGroundLayer();

        // Render below-player objects
        renderBelowPlayerLayer();

        batch.end();
    }
    private void renderBelowPlayerLayer() {
        Rectangle viewBounds = calculateViewBounds();
        List<WorldObject> objects = worldService.getVisibleObjects(viewBounds);

        if (objects.isEmpty() && worldService.isMultiplayerMode()) {
            log.debug("No visible objects in multiplayer mode - viewport: {}", viewBounds);
        }

        objects.stream()
            .filter(obj -> obj.getType().getRenderLayer() == ObjectType.RenderLayer.BELOW_PLAYER)
            .sorted(Comparator.comparingInt(WorldObject::getTileY))
            .forEach(obj -> {
                renderObjectWithFade(obj);
                log.trace("Rendered object {} at ({},{})",
                    obj.getType(), obj.getTileX(), obj.getTileY());
            });
    }
    public void renderTreeTops(float delta) {
        currentDelta = delta;
        batch.begin();
        renderAbovePlayerLayer();
        renderTreeTopsForLayeredTrees();
        batch.end();
    }
    private void renderTreeTopsForLayeredTrees() {
        Rectangle viewBounds = calculateViewBounds();
        List<WorldObject> objects = worldService.getVisibleObjects(viewBounds);

        objects.stream()
            .filter(obj -> obj.getType().getRenderLayer() == ObjectType.RenderLayer.LAYERED)
            .sorted(Comparator.comparingInt(WorldObject::getTileY))
            .forEach(this::renderTreeTop);
    }
    private Rectangle calculateViewBounds() {
        OrthographicCamera camera = worldService.getCamera();
        float width = camera.viewportWidth * camera.zoom;
        float height = camera.viewportHeight * camera.zoom;

        return new Rectangle(
            camera.position.x - (width / 2) - (TILE_SIZE * VIEW_PADDING),
            camera.position.y - (height / 2) - (TILE_SIZE * VIEW_PADDING),
            width + (TILE_SIZE * VIEW_PADDING * 2),
            height + (TILE_SIZE * VIEW_PADDING * 2)
        );
    }
    private void renderChunk(ChunkData chunk) {
        if (chunk == null || chunk.getTiles() == null) return;

        int chunkPixelX = chunk.getChunkX() * CHUNK_SIZE * TILE_SIZE;
        int chunkPixelY = chunk.getChunkY() * CHUNK_SIZE * TILE_SIZE;

        int[][] tiles = chunk.getTiles();
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                TextureRegion region = tileManager.getRegionForTile(tiles[x][y]);
                if (region != null) {
                    float worldX = chunkPixelX + (x * TILE_SIZE);
                    float worldY = chunkPixelY + (y * TILE_SIZE);
                    batch.draw(region, worldX, worldY, TILE_SIZE, TILE_SIZE);
                } else {
                    log.error("No texture region for tile ID {} at {},{}",
                        tiles[x][y], x, y);
                }
            }
        }
    }
    private void renderGroundLayer() {
        Rectangle viewBounds = calculateViewBounds();
        Map<String, ChunkData> visibleChunks = worldService.getVisibleChunks(viewBounds);

        // Draw void color for unloaded chunks
        batch.setColor(VOID_COLOR);
        for (int x = (int) viewBounds.x; x < viewBounds.x + viewBounds.width; x += CHUNK_SIZE * TILE_SIZE) {
            for (int y = (int) viewBounds.y; y < viewBounds.y + viewBounds.height; y += CHUNK_SIZE * TILE_SIZE) {
                int chunkX = x / (CHUNK_SIZE * TILE_SIZE);
                int chunkY = y / (CHUNK_SIZE * TILE_SIZE);
                String key = chunkX + "," + chunkY;
                if (!visibleChunks.containsKey(key)) {
                    batch.draw(tileManager.getRegionForTile(0), // Use a blank tile texture
                        x, y,
                        CHUNK_SIZE * TILE_SIZE,
                        CHUNK_SIZE * TILE_SIZE);
                }
            }
        }

        // Reset color and draw loaded chunks
        batch.setColor(Color.WHITE);
        for (ChunkData chunk : visibleChunks.values()) {
            renderChunk(chunk);
        }
    }

    private void renderAbovePlayerLayer() {
        Rectangle viewBounds = calculateViewBounds();
        List<WorldObject> objects = worldService.getVisibleObjects(viewBounds);

        objects.stream()
            .filter(obj -> obj.getType().getRenderLayer() == ObjectType.RenderLayer.ABOVE_PLAYER)
            .sorted(Comparator.comparingInt(WorldObject::getTileY))
            .forEach(this::renderObjectWithFade);
    }

    private void renderTreeTop(WorldObject tree) {
        TextureRegion fullTexture = getObjectTexture(tree);
        if (fullTexture == null) {
            log.warn("Missing texture for tree object: {}", tree.getType());
            return;
        }

        tree.setTimeSinceVisible(tree.getTimeSinceVisible() + currentDelta);

        int totalHeight = fullTexture.getRegionHeight();
        int topHeight = (totalHeight * 2) / 3;

        TextureRegion topRegion = new TextureRegion(
            fullTexture.getTexture(),
            fullTexture.getRegionX(),
            fullTexture.getRegionY(),
            fullTexture.getRegionWidth(),
            topHeight
        );

        float renderX = tree.getTileX() * TILE_SIZE - TILE_SIZE;
        float renderY = tree.getTileY() * TILE_SIZE + TILE_SIZE;

        float alpha = tree.getFadeAlpha();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, alpha);

        batch.draw(topRegion,
            renderX, renderY,
            tree.getType().getWidthInTiles() * TILE_SIZE,
            TILE_SIZE * 2);

        batch.setColor(c.r, c.g, c.b, 1f);
    }

    private void renderObjectWithFade(WorldObject obj) {
        String regionName = obj.getType().getTextureRegionName();
        TextureRegion texture = objectTextureManager.getTexture(regionName);

        if (texture == null) {
            log.warn("No texture found for object type: {} (region: {})",
                obj.getType(), regionName);
            return;
        }

        obj.setTimeSinceVisible(obj.getTimeSinceVisible() + currentDelta);
        float alpha = obj.getFadeAlpha();

        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, alpha);

        float x = obj.getTileX() * TILE_SIZE;
        float y = obj.getTileY() * TILE_SIZE;
        int width = obj.getType().getWidthInTiles() * TILE_SIZE;
        int height = obj.getType().getHeightInTiles() * TILE_SIZE;

        batch.draw(texture, x, y, width, height);
        batch.setColor(c.r, c.g, c.b, 1f);
    }

    private void renderTreeBase(WorldObject tree) {
        TextureRegion fullTexture = getObjectTexture(tree);
        if (fullTexture == null) return;

        tree.setTimeSinceVisible(tree.getTimeSinceVisible() + currentDelta);

        int totalHeight = fullTexture.getRegionHeight();
        int baseHeight = totalHeight / 3;

        TextureRegion baseRegion = new TextureRegion(
            fullTexture.getTexture(),
            fullTexture.getRegionX(),
            fullTexture.getRegionY() + totalHeight - baseHeight,
            fullTexture.getRegionWidth(),
            baseHeight
        );

        float renderX = tree.getTileX() * TILE_SIZE - TILE_SIZE;
        float renderY = tree.getTileY() * TILE_SIZE;

        float alpha = tree.getFadeAlpha();
        Color c = batch.getColor();
        batch.setColor(c.r, c.g, c.b, alpha);

        batch.draw(baseRegion,
            renderX, renderY,
            tree.getType().getWidthInTiles() * TILE_SIZE,
            TILE_SIZE);

        batch.setColor(c.r, c.g, c.b, 1f);
    }

    private TextureRegion getObjectTexture(WorldObject obj) {
        String regionName = obj.getType().getTextureRegionName();
        return objectTextureManager.getTexture(regionName);
    }

    public void dispose() {
        batch.dispose();
    }
}
