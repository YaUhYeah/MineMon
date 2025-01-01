package io.github.minemon.world.model;

import java.util.HashMap;
import java.util.Map;

import io.github.minemon.player.model.PlayerData;
import lombok.Getter;
import lombok.Setter;

@Getter
public class WorldData {
    @Setter
    private String worldName;
    @Setter
    private long seed;
    private final Map<String, PlayerData> players = new HashMap<>();
    private final Map<String, ChunkData> chunks = new HashMap<>();

    @Setter
    private long createdDate;
    @Setter
    private long lastPlayed;
    @Setter
    private long playedTime; 
}
