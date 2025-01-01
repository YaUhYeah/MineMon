package io.github.minemon.player.model;

import com.badlogic.gdx.math.Vector2;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerModel {
    public final int TILE_SIZE = 32;

    private Vector2 position;
    private Vector2 startPosition;
    private Vector2 targetPosition;

    private PlayerDirection direction;
    private boolean moving;
    private boolean running;

    private float movementTime = 0f;
    private float movementDuration = 0.2f;
    private float runSpeedMultiplier = 1.75f;

    private float stateTime = 0f;

    public PlayerModel(int startTileX, int startTileY) {
        float x = startTileX * TILE_SIZE;
        float y = startTileY * TILE_SIZE;
        this.position = new Vector2(x, y);
        this.targetPosition = new Vector2(x, y);
        this.startPosition = new Vector2(x, y);
        this.direction = PlayerDirection.DOWN;
        this.moving = false;
        this.running = false;
    }

    public void setTargetPosition(float x, float y) {
        this.targetPosition.set(x, y);
    }

    public void setStartPosition(float x, float y) {
        this.startPosition.set(x, y);
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }
}
