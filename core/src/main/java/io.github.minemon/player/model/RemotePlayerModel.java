package io.github.minemon.player.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RemotePlayerModel {
    public float currentX, currentY;
    public float targetX, targetY;
    public float lastX, lastY;
    public boolean moving;
    public boolean wasMoving;
    public boolean running;
    public PlayerDirection direction = PlayerDirection.DOWN;
    public float animationTime;
    public float updateTime;
    public float movementThreshold = 0.001f;
    public void updatePosition(float newX, float newY, float delta) {
        lastX = currentX;
        lastY = currentY;

        float lerpFactor = 10f * delta;
        currentX += (newX - currentX) * lerpFactor;
        currentY += (newY - currentY) * lerpFactor;

        boolean isMoving = Math.abs(currentX - lastX) > movementThreshold ||
            Math.abs(currentY - lastY) > movementThreshold;

        wasMoving = moving;
        moving = isMoving;

        
        if (moving) {
            animationTime += delta;
        } else if (wasMoving != moving) {
            animationTime = 0;  
        }
    }
}
