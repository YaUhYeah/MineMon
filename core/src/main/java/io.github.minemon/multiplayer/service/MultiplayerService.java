package io.github.minemon.multiplayer.service;

import io.github.minemon.player.model.PlayerData;
import io.github.minemon.multiplayer.model.ChunkUpdate;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;

import java.util.List;
import java.util.Map;

public interface MultiplayerService {
    void playerConnected(String username);
    void playerDisconnected(String username);

    PlayerData getPlayerData(String username);
    void updatePlayerData(PlayerData data);

    ChunkUpdate getChunkData(int chunkX, int chunkY);
    Map<String, PlayerSyncData> getAllPlayerStates();
    List<WorldObjectUpdate> getAllWorldObjectUpdates();

    void broadcastPlayerState(PlayerData data);
    void broadcastChunkUpdate(ChunkUpdate chunk);
    void broadcastWorldObjectUpdate(WorldObjectUpdate objUpdate);
    void tick(float delta);
}
