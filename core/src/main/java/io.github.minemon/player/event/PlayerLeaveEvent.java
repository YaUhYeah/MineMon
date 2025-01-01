package io.github.minemon.player.event;

import io.github.minemon.event.Event;
import lombok.Getter;

@Getter
public class PlayerLeaveEvent implements Event {
    private final String username;

    public PlayerLeaveEvent(String username) {
        this.username = username;
    }

}
