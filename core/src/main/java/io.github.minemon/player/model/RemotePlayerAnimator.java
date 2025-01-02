package io.github.minemon.player.model;

import lombok.Getter;

public class RemotePlayerAnimator {
    private static final float POSITION_LERP_SPEED = 10f;
    private static final float MIN_MOVEMENT_THRESHOLD = 0.001f;

    // Getters for rendering
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

    public RemotePlayerAnimator() {
        this.direction = PlayerDirection.DOWN;
        this.lastDirection = direction;
        this.moving = false;
        this.wasMoving = false;
        this.running = false;
        this.animationTime = 0f;
    }

    public void updateState(float newTargetX, float newTargetY, boolean isRunning, PlayerDirection newDirection, float delta) {
        // Save last position and direction
        lastX = currentX;
        lastY = currentY;
        lastDirection = direction;
        wasMoving = moving;

        // Update running state and direction
        this.running = isRunning;
        this.direction = newDirection;

        // Update target position
        this.targetX = newTargetX;
        this.targetY = newTargetY;

        // Smoothly interpolate current position to target
        float lerpSpeed = POSITION_LERP_SPEED * delta;
        currentX += (targetX - currentX) * lerpSpeed;
        currentY += (targetY - currentY) * lerpSpeed;

        // Determine if we're actually moving based on position changes
        float dx = Math.abs(currentX - lastX);
        float dy = Math.abs(currentY - lastY);
        moving = dx > MIN_MOVEMENT_THRESHOLD || dy > MIN_MOVEMENT_THRESHOLD;

        // Update animation time
        if (moving) {
            animationTime += delta * (running ? 2f : 1f); // Run animations play faster
        } else if (wasMoving != moving || lastDirection != direction) {
            // Reset animation when starting/stopping or changing direction
            animationTime = 0f;
        }
    }

    // For interpolation and state changes
    public void setPosition(float x, float y) {
        this.currentX = x;
        this.targetX = x;
        this.lastX = x;
        this.currentY = y;
        this.targetY = y;
        this.lastY = y;
    }

    public void resetAnimation() {
        animationTime = 0f;
        moving = false;
        wasMoving = false;
    }
}
