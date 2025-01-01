package io.github.minemon.world.event;

import io.github.minemon.event.Event;
import lombok.Getter;

@Getter
public class ChunkLoadEvent implements Event {
    private final int chunkX;
    private final int chunkY;

    public ChunkLoadEvent(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
    }

}
