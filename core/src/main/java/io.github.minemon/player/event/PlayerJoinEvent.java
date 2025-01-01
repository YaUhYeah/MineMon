package io.github.minemon.player.event;

import io.github.minemon.event.Event;
import lombok.Getter;

@Getter
public class PlayerJoinEvent implements Event {
    private final String username;

    public PlayerJoinEvent(String username) {
        this.username = username;
    }

}
