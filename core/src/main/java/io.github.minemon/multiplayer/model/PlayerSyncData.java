package io.github.minemon.multiplayer.model;

import io.github.minemon.player.model.PlayerData;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerSyncData {
    private String username;
    private float x;
    private float y;
    private boolean running;
    private String direction;
    private boolean moving;
    private float animationTime = 0f;
    private float lastUpdateTime = 0f;
    private boolean wasMoving;
    private String lastDirection;

    public static PlayerSyncData fromPlayerData(PlayerData pd) {
        PlayerSyncData sync = new PlayerSyncData();
        sync.setUsername(pd.getUsername());
        sync.setX(pd.getX());
        sync.setY(pd.getY());
        sync.setRunning(pd.isWantsToRun());
        sync.setDirection(pd.getDirection() != null ? pd.getDirection().name() : "DOWN");
        sync.setMoving(pd.isMoving());

        if (sync.isMoving() != sync.wasMoving ||
            !sync.direction.equals(sync.lastDirection)) {
            sync.setAnimationTime(0f);
        }

        sync.setWasMoving(sync.isMoving());
        sync.setLastDirection(sync.direction);
        return sync;
    }

    public void updateAnimationTime(float delta) {
        if (moving) {
            animationTime += delta;
        }
    }
}
