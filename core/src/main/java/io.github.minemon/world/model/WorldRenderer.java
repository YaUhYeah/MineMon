package io.github.minemon.world.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.world.service.TileManager;
import io.github.minemon.world.service.WorldService;
import io.github.minemon.world.service.impl.ObjectTextureManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class WorldRenderer {
    private static final int TILE_SIZE = 32;
    private static final int CHUNK_SIZE = 16;
    private static final int VIEW_PADDING = 5;
    private static final Color VOID_COLOR = new Color(0.1f, 0.1f, 0.1f, 1f);

    private final TileManager tileManager;
    private final WorldService worldService;
    private final ObjectTextureManager objectTextureManager;
    private final List<TreeTopRender> treeTopQueue = new ArrayList<>();

    private SpriteBatch batch;
    private boolean initialized = false;

    @Autowired
    private ObjectRenderState objectRenderState;

    public WorldRenderer(WorldService worldService,
                         TileManager tileManager,
                         ObjectTextureManager objectTextureManager) {
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

    public void cleanup() {
        objectRenderState.reset();
    }

    /**
     * Main entry point for rendering the world. Now includes a 'delta' param.
     */
    public void render(OrthographicCamera camera, float delta) {
        if (!initialized || batch == null) {
            initialize();
        }

        // Clear any faded-out objects if needed
        Rectangle viewBounds = calculateViewBounds();
        objectRenderState.clearInvisibleObjects(viewBounds);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        treeTopQueue.clear();

        // Render the ground tiles (chunks)
        renderGroundLayer();

        // Render objects below the player
        renderBelowPlayerLayer(delta);

        batch.end();
    }

    /**
     * After everything else, call this to draw the "tops" of trees above the player.
     */
    public void renderTreeTops(float delta) {
        if (treeTopQueue.isEmpty()) return;

        batch.begin();
        for (TreeTopRender top : treeTopQueue) {
            objectRenderState.renderObject(
                batch,
                top.getSourceObject(),
                top.getTexture(),
                delta,
                top.getX(), top.getY(),
                top.getWidth(), top.getHeight()
            );
        }
        batch.end();
    }

    private void renderBelowPlayerLayer(float delta) {
        Rectangle viewBounds = calculateViewBounds();
        List<WorldObject> objects = worldService.getVisibleObjects(viewBounds);

        // 1) Non-tree objects
        objects.stream()
            .filter(obj -> !isTreeType(obj.getType()))
            .sorted(Comparator.comparingInt(WorldObject::getTileY))
            .forEach(obj -> renderRegularObject(obj, delta));

        // 2) Trees (split base vs. top)
        objects.stream()
            .filter(obj -> isTreeType(obj.getType()))
            .sorted(Comparator.comparingInt(WorldObject::getTileY))
            .forEach(obj -> {
                renderTreeBase(obj, delta);
                queueTreeTop(obj);
            });
    }

    private void renderRegularObject(WorldObject obj, float delta) {
        TextureRegion texture = objectTextureManager.getTexture(
            obj.getType().getTextureRegionName());
        if (texture == null) return;

        // Delegate drawing + fade logic to objectRenderState
        objectRenderState.renderObject(batch, obj, texture, delta);
    }
    private void renderTreeBase(WorldObject tree, float delta) {
        TextureRegion full = objectTextureManager.getTexture(tree.getType().getTextureRegionName());

        int totalW = full.getRegionWidth();
        int totalH = full.getRegionHeight();

        // Example: 70% base, 30% top
        int basePx = (int)(totalH * 0.7f);
        TextureRegion baseRegion = new TextureRegion(full, 0, totalH - basePx, totalW, basePx);

        int tileW = tree.getType().getWidthInTiles();  // 2
        int tileH = tree.getType().getHeightInTiles(); // 3

        int finalW = tileW * 32; // 64
        int finalH = tileH * 32; // 96

        // For the trunk to sit EXACTLY on the bottom 2 tiles:
        // We do NOT shift left by 32. We just place it at tileX*32
        float drawX = tree.getTileX() * 32f;
        float drawY = tree.getTileY() * 32f;

        // The base is 70% of finalH
        float baseHeight = finalH * 0.7f; // e.g. 96 * 0.7 = 67.2 → ~67

        // Draw it flush at (drawX, drawY)
        objectRenderState.renderObject(batch, tree,
            baseRegion, delta,
            drawX, drawY,  // no offset
            finalW, baseHeight
        );
    }
    private void queueTreeTop(WorldObject tree) {
        TextureRegion fullTexture = objectTextureManager.getTexture(
            tree.getType().getTextureRegionName()
        );
        if (fullTexture == null) return;

        int totalWidth  = fullTexture.getRegionWidth();
        int totalHeight = fullTexture.getRegionHeight();

        // For a 70% base, top is 30%
        int basePx = (int) (totalHeight * 0.7f);
        int topPx  = totalHeight - basePx; // 30%

        TextureRegion topRegion = new TextureRegion(
            fullTexture,
            0,
            0,          // top is physically at Y=0 in the image
            totalWidth,
            topPx
        );

        // Convert tile size
        int tileW = tree.getType().getWidthInTiles();    // e.g., 2
        int tileH = tree.getType().getHeightInTiles();   // e.g., 3
        int finalWidthPx  = tileW * TILE_SIZE;           // 64 for a 2-tile wide
        int finalHeightPx = tileH * TILE_SIZE;           // 96 for a 3-tile tall

        // The base is 70%, top is 30%
        int baseHeightPx = (int) (finalHeightPx * 0.7f);         int topHeightPx  = finalHeightPx - baseHeightPx;

        float drawX = tree.getTileX() * TILE_SIZE;
        float drawY = tree.getTileY() * TILE_SIZE + baseHeightPx;

        drawY -= 1f;

        TreeTopRender topData = new TreeTopRender(
            topRegion,
            drawX, drawY,
            finalWidthPx,
            topHeightPx,
            tree
        );
        treeTopQueue.add(topData);
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

    private void renderGroundLayer() {
        Rectangle viewBounds = calculateViewBounds();
        Map<String, ChunkData> visibleChunks = worldService.getVisibleChunks(viewBounds);

        // Draw "void" for unloaded chunks
        batch.setColor(VOID_COLOR);
        for (int x = (int) viewBounds.x; x < viewBounds.x + viewBounds.width; x += CHUNK_SIZE * TILE_SIZE) {
            for (int y = (int) viewBounds.y; y < viewBounds.y + viewBounds.height; y += CHUNK_SIZE * TILE_SIZE) {
                int chunkX = x / (CHUNK_SIZE * TILE_SIZE);
                int chunkY = y / (CHUNK_SIZE * TILE_SIZE);
                String key = chunkX + "," + chunkY;
                if (!visibleChunks.containsKey(key)) {
                    // draw a placeholder
                    batch.draw(tileManager.getRegionForTile(0), x, y,
                        CHUNK_SIZE * TILE_SIZE, CHUNK_SIZE * TILE_SIZE);
                }
            }
        }
        batch.setColor(Color.WHITE);

        // Now draw actual loaded chunks
        for (ChunkData chunk : visibleChunks.values()) {
            renderChunk(chunk);
        }
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
                    log.error("No texture region for tile ID {} at {},{}", tiles[x][y], x, y);
                }
            }
        }
    }

    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        cleanup();
    }

    @AllArgsConstructor
    @Getter
    private static class TreeTopRender {
        private final TextureRegion texture;
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        /**
         * The same WorldObject, so we can fade it using the same ID.
         */
        private final WorldObject sourceObject;
    }
}
