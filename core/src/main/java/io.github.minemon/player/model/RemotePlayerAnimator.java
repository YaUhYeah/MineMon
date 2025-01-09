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
    private float stationaryTime; 

    public RemotePlayerAnimator() {
        this.direction = PlayerDirection.DOWN;
        this.lastDirection = direction;
        this.moving = false;
        this.wasMoving = false;
        this.running = false;
        this.animationTime = 0f;
        this.stationaryTime = 0f;
    }

    
    public void updateState(float newTargetX, float newTargetY,
                            boolean isRunning, PlayerDirection newDirection,
                            boolean serverMoving, float delta) {

        
        lastX = currentX;
        lastY = currentY;
        lastDirection = direction;
        wasMoving = moving;

        
        this.running = isRunning;
        this.direction = newDirection;
        this.targetX = newTargetX;
        this.targetY = newTargetY;

        
        float dx = Math.abs(targetX - currentX);
        float dy = Math.abs(targetY - currentY);
        boolean hasPositionChanged = (dx > MIN_MOVEMENT_THRESHOLD || dy > MIN_MOVEMENT_THRESHOLD);

        
        if (hasPositionChanged) {
            float lerpSpeed = POSITION_LERP_SPEED * delta;
            currentX += (targetX - currentX) * lerpSpeed;
            currentY += (targetY - currentY) * lerpSpeed;
            stationaryTime = 0f;
        } else {
            
            stationaryTime += delta;
        }

        
        
        if (!serverMoving && stationaryTime > 0.1f) {
            moving = false;
            
            currentX = targetX;
            currentY = targetY;
        }
        
        else if (hasPositionChanged || serverMoving) {
            moving = true;
        }

        
        
        float actualDX = Math.abs(currentX - lastX);
        float actualDY = Math.abs(currentY - lastY);
        boolean actuallyMoved = (actualDX > MIN_MOVEMENT_THRESHOLD || actualDY > MIN_MOVEMENT_THRESHOLD);

        if (serverMoving && !actuallyMoved) {
            moving = false;
            stationaryTime += delta;   
        }

        
        if (moving) {
            
            animationTime += delta * (running ? 2f : 1f);
        } else {
            
            if (wasMoving != moving || lastDirection != direction) {
                animationTime = 0f;
            }
        }
    }

    
    public void setPosition(float x, float y) {
        this.currentX = x;
        this.targetX = x;
        this.lastX = x;
        this.currentY = y;
        this.targetY = y;
        this.lastY = y;
        this.stationaryTime = 0f;
    }

    
    public void resetAnimation() {
        animationTime = 0f;
        moving = false;
        wasMoving = false;
        stationaryTime = 0f;
    }
}
