package io.github.minemon.player.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PlayerData {
    private String username;

    private float x;
    private float y;
    private boolean wantsToRun;
    private boolean moving;
    private String inventoryData;
    private PlayerDirection direction = PlayerDirection.DOWN;

    public PlayerData() {}

    public PlayerData(String username, float x, float y, PlayerDirection dir) {
        this.username = username;
        this.x = x;
        this.y = y;
        this.wantsToRun = false;
        this.moving = false;
        this.direction = dir;
    }

    public PlayerData copy() {
        PlayerData copy = new PlayerData();
        copy.username = this.username;
        copy.x = this.x;
        copy.y = this.y;
        copy.direction = this.direction;
        copy.wantsToRun = this.wantsToRun;
        copy.moving = this.moving;
        copy.inventoryData = this.inventoryData;
        return copy;
    }
}
