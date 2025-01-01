package io.github.minemon.world.model;


import lombok.Data;

@Data
public class WorldMetadata {

    private String worldName;

    private long seed;
    private long createdDate;
    private long lastPlayed;
    private long playedTime;
}
