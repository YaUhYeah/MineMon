package io.github.minemon.player.model;

import lombok.Getter;

public class RemotePlayerAnimator {
    private static final float MIN_MOVEMENT_THRESHOLD = 0.001f;
    private static final float POSITION_LERP_SPEED = 15f;

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
    private float stationaryTime; // tracks how long we have been at rest

    public RemotePlayerAnimator() {
        this.direction = PlayerDirection.DOWN;
        this.lastDirection = direction;
        this.moving = false;
        this.wasMoving = false;
        this.running = false;
        this.animationTime = 0f;
        this.stationaryTime = 0f;
    }

    /**
     * Updates the remote player's position, direction, and animation state.
     *
     * @param newTargetX     Where the server says we should be (X)
     * @param newTargetY     Where the server says we should be (Y)
     * @param isRunning      True if server says player is running
     * @param newDirection   The direction from the server
     * @param serverMoving   True if server is telling us the player is "moving"
     * @param delta          Frame time
     */
    public void updateState(float newTargetX, float newTargetY,
                            boolean isRunning, PlayerDirection newDirection,
                            boolean serverMoving, float delta) {

        // Store old positions to detect if we truly changed
        lastX = currentX;
        lastY = currentY;
        lastDirection = direction;
        wasMoving = moving;

        // Update basic states from server
        this.running = isRunning;
        this.direction = newDirection;
        this.targetX = newTargetX;
        this.targetY = newTargetY;

        // Check how far we are from target
        float dx = Math.abs(targetX - currentX);
        float dy = Math.abs(targetY - currentY);
        boolean hasPositionChanged = (dx > MIN_MOVEMENT_THRESHOLD || dy > MIN_MOVEMENT_THRESHOLD);

        // Smooth interpolation
        if (hasPositionChanged) {
            float lerpSpeed = POSITION_LERP_SPEED * delta;
            currentX += (targetX - currentX) * lerpSpeed;
            currentY += (targetY - currentY) * lerpSpeed;
            stationaryTime = 0f;
        } else {
            // If we’re not changing position at all, keep track of idle time
            stationaryTime += delta;
        }

        // Now decide "moving" or "not" based on server + actual movement
        // 1) If the server claims no movement, or we’ve been idle long enough, forcibly stop
        if (!serverMoving && stationaryTime > 0.1f) {
            moving = false;
            // Snap to target
            currentX = targetX;
            currentY = targetY;
        }
        // 2) If server says "moving" OR we are actually adjusting position, set true
        else if (hasPositionChanged || serverMoving) {
            moving = true;
        }

        // *** Additional final check: if server says "moving" but we truly didn't move, override. ***
        // This prevents "running in place" if the server sets moving=true but we actually haven't changed positions.
        float actualDX = Math.abs(currentX - lastX);
        float actualDY = Math.abs(currentY - lastY);
        boolean actuallyMoved = (actualDX > MIN_MOVEMENT_THRESHOLD || actualDY > MIN_MOVEMENT_THRESHOLD);

        if (serverMoving && !actuallyMoved) {
            moving = false;
            stationaryTime += delta;   // accumulate idle time if truly no movement
        }

        // Update animation time
        if (moving) {
            // Running speeds up animation by 2x (example)
            animationTime += delta * (running ? 2f : 1f);
        } else {
            // If we just stopped moving or changed direction, reset animation to idle frame
            if (wasMoving != moving || lastDirection != direction) {
                animationTime = 0f;
            }
        }
    }

    /**
     * Directly sets our position (e.g., on initial spawn).
     */
    public void setPosition(float x, float y) {
        this.currentX = x;
        this.targetX = x;
        this.lastX = x;
        this.currentY = y;
        this.targetY = y;
        this.lastY = y;
        this.stationaryTime = 0f;
    }

    /**
     * Resets the animation and movement state completely.
     */
    public void resetAnimation() {
        animationTime = 0f;
        moving = false;
        wasMoving = false;
        stationaryTime = 0f;
    }
}
