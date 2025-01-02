package io.github.minemon.player.model;

import lombok.Getter;

public class RemotePlayerAnimator {

    @Getter
    private float currentX, currentY;
    private float targetX, targetY;
    private float lastX, lastY;
    @Getter
    private float animationTime;
    @Getter
    private boolean moving;
    private boolean wasMoving;
    @Getter
    private boolean running;
    @Getter
    private PlayerDirection direction;
    private PlayerDirection lastDirection;
    private float movementTimer; // New timer for movement detection

    public RemotePlayerAnimator() {
        this.direction = PlayerDirection.DOWN;
        this.lastDirection = direction;
        this.moving = false;
        this.wasMoving = false;
        this.running = false;
        this.animationTime = 0f;
        this.movementTimer = 0f;
    }

    public void updateState(float newTargetX, float newTargetY, boolean isRunning,
                            PlayerDirection newDirection, boolean serverMoving, float delta) {
        lastX = currentX;
        lastY = currentY;
        lastDirection = direction;
        wasMoving = moving;
        this.running = isRunning;
        this.direction = newDirection;
        this.targetX = newTargetX;
        this.targetY = newTargetY;

        float POSITION_LERP_SPEED = 15f;
        float lerpSpeed = POSITION_LERP_SPEED * delta;
        currentX += (targetX - currentX) * lerpSpeed;
        currentY += (targetY - currentY) * lerpSpeed;

        float dx = Math.abs(currentX - lastX);
        float dy = Math.abs(currentY - lastY);
        float MIN_MOVEMENT_THRESHOLD = 0.0001f;
        boolean isMovingNow = dx > MIN_MOVEMENT_THRESHOLD || dy > MIN_MOVEMENT_THRESHOLD;

        if (serverMoving || isMovingNow) {
            movementTimer = 0.1f; // Set a small buffer time
            moving = true;
        } else {
            movementTimer -= delta;
            if (movementTimer <= 0) {
                moving = false;
                movementTimer = 0;
            }
        }

        // Update animation time
        if (moving) {
            animationTime += delta * (running ? 2f : 1f);
        } else if (wasMoving != moving || lastDirection != direction) {
            animationTime = 0f;
        }
    }


    public void setPosition(float x, float y) {
        this.currentX = x;
        this.targetX = x;
        this.lastX = x;
        this.currentY = y;
        this.targetY = y;
        this.lastY = y;
        this.movementTimer = 0f;
    }

    public void resetAnimation() {
        animationTime = 0f;
        moving = false;
        wasMoving = false;
        movementTimer = 0f;
    }
}
