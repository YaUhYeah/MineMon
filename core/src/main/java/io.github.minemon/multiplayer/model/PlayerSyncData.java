package io.github.minemon.multiplayer.model;

import lombok.Getter;
import lombok.Setter;
import io.github.minemon.player.model.PlayerData;

/**
 * Each remote player's sync data as sent by the server.
 * Now includes local animation fields for the client to track
 * each player's animation time and movement states.
 */
@Getter @Setter
public class PlayerSyncData {
    private String username;
    private float x;
    private float y;
    private boolean running;
    private String direction;
    private boolean moving;

    private float animationTime = 0f;

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
        return sync;
    }
}
