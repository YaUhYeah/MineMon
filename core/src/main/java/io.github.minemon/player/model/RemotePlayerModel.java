package io.github.minemon.player.model;

public class RemotePlayerModel {
    public float currentX, currentY;
    public float targetX, targetY;
    public boolean moving;
    public boolean running;
    public PlayerDirection direction = PlayerDirection.DOWN;
    public float animationTime;
}
