package io.github.minemon.player.event;

import io.github.minemon.event.Event;
import io.github.minemon.player.model.PlayerData;
import lombok.Getter;

@Getter
public class PlayerMoveEvent implements Event {
    private final PlayerData playerData;

    public PlayerMoveEvent(PlayerData playerData) {
        this.playerData = playerData;
    }

}
