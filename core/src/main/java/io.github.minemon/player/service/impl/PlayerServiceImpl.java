package io.github.minemon.player.service.impl;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import io.github.minemon.event.EventBus;
import io.github.minemon.input.InputService;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.config.PlayerProperties;
import io.github.minemon.player.event.PlayerMoveEvent;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.model.PlayerDirection;
import io.github.minemon.player.model.PlayerModel;
import io.github.minemon.player.service.PlayerAnimationService;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.model.ChunkData;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.ChunkLoadingManager;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PlayerServiceImpl implements PlayerService {
    public final int TILE_SIZE = 32;

    private final InventoryService inventoryService;
    private final PlayerModel playerModel;
    private final PlayerAnimationService animationService;
    private final WorldService worldService;
    private final float walkStepDuration;
    private final float runStepDuration;
    private final InputService inputService;
    private String username;
    private PlayerDirection bufferedDirection = null;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private ChunkLoadingManager chunkLoadingManager;
    @Autowired
    private MultiplayerClient multiplayerClient;

    public PlayerServiceImpl(
        PlayerAnimationService animationService,
        InputService inputService,
        PlayerProperties playerProperties,
        WorldService worldService,
        InventoryService inventoryService
    ) {
        this.playerModel = new PlayerModel(0, 0);
        this.animationService = animationService;
        this.inventoryService = inventoryService;
        this.inputService = inputService;
        this.username = playerProperties.getUsername();
        this.walkStepDuration = playerProperties.getWalkStepDuration();
        this.runStepDuration = playerProperties.getRunStepDuration();
        this.playerModel.setRunning(false);
        this.worldService = worldService;
    }

    @Override
    public void move(PlayerDirection direction) {
        // If currently mid-move, buffer the new direction for after finishing
        if (playerModel.isMoving()) {
            log.debug("Currently moving. Buffering direction: {}", direction);
            this.bufferedDirection = direction;
            return;
        }

        float currentX = playerModel.getPosition().x;
        float currentY = playerModel.getPosition().y;
        float tileSize = TILE_SIZE;

        int currentTileX = (int) (currentX / tileSize);
        int currentTileY = (int) (currentY / tileSize);

        // Calculate the attempted tile
        int targetTileX = currentTileX;
        int targetTileY = currentTileY;
        switch (direction) {
            case UP -> targetTileY += 1;
            case DOWN -> targetTileY -= 1;
            case LEFT -> targetTileX -= 1;
            case RIGHT -> targetTileX += 1;
        }

        // Always set direction so we appear to face that way even if blocked
        playerModel.setDirection(direction);

        if (isColliding(targetTileX, targetTileY)) {
            log.debug("Collision at ({}, {}): no movement, but direction updated to {}",
                targetTileX, targetTileY, direction);
            // No movement, remain idle, but direction is changed
            playerModel.setMoving(false);
            return;
        }

        // If passable, we do run/walk
        playerModel.setRunning(inputService.isRunning());
        float targetX = targetTileX * tileSize;
        float targetY = targetTileY * tileSize;

        playerModel.setStartPosition(currentX, currentY);
        playerModel.setTargetPosition(targetX, targetY);

        float duration = playerModel.isRunning() ? runStepDuration : walkStepDuration;
        playerModel.setMovementDuration(duration);

        // DO NOT reset stateTime, so the animation doesn't restart every step:
        // playerModel.setStateTime(0f);

        // We still reset movementTime for tile interpolation:
        playerModel.setMovementTime(0f);

        playerModel.setMoving(true);
        if (worldService.isMultiplayerMode()) {
            chunkLoadingManager.preloadChunksAroundPosition(
                playerModel.getTargetPosition().x,
                playerModel.getTargetPosition().y
            );
        }
        log.debug("Initiated movement: {}, Target=({}, {}), Duration={}",
            direction, targetX, targetY, duration);
    }


    private boolean isColliding(int tileX, int tileY) {
        int chunkX = tileX / 16;
        int chunkY = tileY / 16;
        int[][] chunkTiles = worldService.getChunkTiles(chunkX, chunkY);
        if (chunkTiles == null) return true;

        int localX = Math.floorMod(tileX, 16);
        int localY = Math.floorMod(tileY, 16);
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16) return true;

        int tileID = chunkTiles[localX][localY];
        if (!worldService.getTileManager().isPassable(tileID)) {
            return true;
        }

        float tileSize = TILE_SIZE;
        Rectangle targetTileRect = new Rectangle(tileX * tileSize, tileY * tileSize, tileSize, tileSize);

        List<WorldObject> nearbyObjects = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int neighborChunkX = chunkX + dx;
                int neighborChunkY = chunkY + dy;
                String chunkKey = neighborChunkX + "," + neighborChunkY;
                ChunkData chunkData = worldService.getWorldData().getChunks().get(chunkKey);
                if (chunkData != null && chunkData.getObjects() != null) {
                    nearbyObjects.addAll(chunkData.getObjects());
                }
            }
        }

        for (WorldObject obj : nearbyObjects) {
            if (!obj.isCollidable()) continue;
            Rectangle collisionBox = obj.getCollisionBox();
            if (collisionBox == null) continue;
            if (collisionBox.overlaps(targetTileRect)) {
                log.debug("Collision detected with object {} at tile ({}, {})", obj.getId(), tileX, tileY);
                return true;
            }
        }

        return false;
    }

    @Override
    public void update(float delta) {
        playerModel.setStateTime(playerModel.getStateTime() + delta);

        if (playerModel.isMoving()) {
            float progress = playerModel.getMovementTime() / playerModel.getMovementDuration();
            progress = Math.min(progress + (delta / playerModel.getMovementDuration()), 1f);

            float smoothed = smoothstep(progress);

            float newX = lerp(playerModel.getStartPosition().x, playerModel.getTargetPosition().x, smoothed);
            float newY = lerp(playerModel.getStartPosition().y, playerModel.getTargetPosition().y, smoothed);
            playerModel.setPosition(newX, newY);

            playerModel.setMovementTime(playerModel.getMovementTime() + delta);

            if (progress >= 1f) {
                playerModel.setMoving(false);
                playerModel.setPosition(playerModel.getTargetPosition().x, playerModel.getTargetPosition().y);

                // Movement completed, send updated position to the server
                PlayerData pd = getPlayerData();
                worldService.setPlayerData(pd);
                multiplayerClient.sendPlayerMove(
                    pd.getX(),
                    pd.getY(),
                    pd.isWantsToRun(),
                    pd.isMoving(),
                    pd.getDirection().name().toLowerCase()
                );

                if (bufferedDirection != null) {
                    PlayerDirection nextDir = bufferedDirection;
                    bufferedDirection = null;
                    move(nextDir);
                } else {
                    PlayerDirection dir = inputService.getCurrentDirection();
                    if (dir != null) {
                        move(dir);
                    } else {
                        playerModel.setMoving(false);
                        playerModel.setRunning(false);
                    }
                }
            }
        } else {
            PlayerDirection dir = inputService.getCurrentDirection();
            if (dir != null) {
                move(dir);
            } else {
                playerModel.setMoving(false);
                playerModel.setRunning(false);
            }
        }

        if (!playerModel.isMoving()) {
            eventBus.fireEvent(new PlayerMoveEvent(getPlayerData()));
        }  if (worldService.isMultiplayerMode()) {
            PlayerData pd = getPlayerData();
            pd.setInventoryData(inventoryService.serializeInventory());
            worldService.setPlayerData(pd);
        }
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float smoothstep(float x) {
        x = Math.max(0f, Math.min(x, 1f));
        return x * x * (3f - 2f * x);
    }

    @Override
    public void render(SpriteBatch batch) {
        TextureRegion frame = animationService.getCurrentFrame(
            playerModel.getDirection(),
            playerModel.isMoving(),
            playerModel.isRunning(),
            playerModel.getStateTime()
        );
        batch.draw(frame, playerModel.getPosition().x, playerModel.getPosition().y);
    }


    @Override
    public PlayerData getPlayerData() {
        PlayerData pd = new PlayerData(username,
            playerModel.getPosition().x / TILE_SIZE,
            playerModel.getPosition().y / TILE_SIZE,
            playerModel.getDirection());

        pd.setMoving(playerModel.isMoving());
        pd.setWantsToRun(playerModel.isRunning());

        // Save current inventory state
        pd.setInventoryData(inventoryService.serializeInventory());

        return pd;
    }
    @Override
    public void setPlayerData(PlayerData data) {
        if (data.getUsername() != null && !data.getUsername().isEmpty()) {
            this.username = data.getUsername();
        }
        if (data.getInventoryData() != null) {
            inventoryService.deserializeInventory(data.getInventoryData());
            log.debug("Loaded inventory data for player {}", data.getUsername());
        } else {
            log.debug("No inventory data found for player {}", data.getUsername());
        }

        int tileX = (int) data.getX();
        int tileY = (int) data.getY();
        setPosition(tileX, tileY);
        playerModel.setMoving(data.isMoving());
        playerModel.setRunning(data.isWantsToRun());

        playerModel.setDirection(data.getDirection());

        log.debug("Player data updated: username={}, x={}, y={}", data.getUsername(), data.getX(), data.getY());

        worldService.setPlayerData(data);
    }

    @Override
    public void setRunning(boolean running) {
        playerModel.setRunning(running);
        log.debug("Set running to {}", running);
    }

    @Override
    public void setPosition(int tileX, int tileY) {
        float x = tileX * TILE_SIZE;
        float y = tileY * TILE_SIZE;
        playerModel.setPosition(x, y);
        playerModel.setStartPosition(x, y);
        playerModel.setTargetPosition(x, y);
        playerModel.setMoving(false);
        playerModel.setMovementTime(0f);
        playerModel.setStateTime(0f);
        this.bufferedDirection = null;
        log.debug("Set position to ({}, {})", x, y);
    }
}
