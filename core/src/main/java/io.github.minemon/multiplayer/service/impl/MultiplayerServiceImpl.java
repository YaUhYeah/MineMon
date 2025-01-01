package io.github.minemon.multiplayer.service.impl;

import io.github.minemon.multiplayer.model.ChunkUpdate;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;
import io.github.minemon.multiplayer.service.MultiplayerService;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Service
@Slf4j
public class MultiplayerServiceImpl implements MultiplayerService {

    private final WorldService worldService;


    private final boolean isServer;
    private final Set<String> connectedPlayers = Collections.synchronizedSet(new HashSet<>());


    private final List<WorldObjectUpdate> pendingObjectUpdates = Collections.synchronizedList(new ArrayList<>());

    @Autowired
    public MultiplayerServiceImpl(WorldService worldService,
                                  Environment env ) {
        this.worldService = worldService;
        this.isServer = env.acceptsProfiles(Profiles.of("server"));
    }

    @Override
    public void playerConnected(String username) {
        if (!isServer) {
            log.warn("Client attempted to handle player connection - ignoring");
            return;
        }
        worldService.initIfNeeded();
        PlayerData pd = worldService.getPlayerData(username);
        if (pd == null) {
            pd = new PlayerData(username, 0, 0);
            worldService.setPlayerData(pd);
        }
        connectedPlayers.add(username);
    }

    @Override
    public void playerDisconnected(String username) {
        if (!isServer) {
            log.warn("Client attempted to handle player disconnection - ignoring");
            return;
        }
        connectedPlayers.remove(username);
    }

    @Override
    public PlayerData getPlayerData(String username) {
        return worldService.getPlayerData(username);
    }

    @Override
    public void updatePlayerData(PlayerData data) {
        if (!isServer) {
            log.warn("Client attempted to handle player disconnection - ignoring");
            return;
        }


        worldService.setPlayerData(data);
    }

    @Override
    public ChunkUpdate getChunkData(int chunkX, int chunkY) {
        if (!isServer) {
            log.warn("Client attempted to handle player disconnection - ignoring");
            return null;
        }
        int[][] tiles = worldService.getChunkTiles(chunkX, chunkY);
        if (tiles == null) return null;

        var wd = worldService.getWorldData();
        if (wd == null) return null;

        String key = chunkX + "," + chunkY;
        var chunkData = wd.getChunks().get(key);
        if (chunkData == null) return null;

        List<WorldObject> objs = chunkData.getObjects();

        ChunkUpdate update = new ChunkUpdate();
        update.setChunkX(chunkX);
        update.setChunkY(chunkY);
        update.setTiles(tiles);
        update.setObjects(objs);
        return update;
    }

    @Override
    public Map<String, PlayerSyncData> getAllPlayerStates() {
        if (!isServer) {
            log.warn("Client attempted to get all player states - ignoring");
            return Collections.emptyMap();
        }
        Map<String, PlayerSyncData> states = new HashMap<>();
        for (String user : connectedPlayers) {
            PlayerData pd = worldService.getPlayerData(user);
            if (pd != null) {
                states.put(user, PlayerSyncData.fromPlayerData(pd));
            }
        }
        return states;
    }

    @Override
    public List<WorldObjectUpdate> getAllWorldObjectUpdates() {
        List<WorldObjectUpdate> snapshot;
        synchronized (pendingObjectUpdates) {
            snapshot = new ArrayList<>(pendingObjectUpdates);
            pendingObjectUpdates.clear();
        }
        return snapshot;
    }

    @Override
    public void broadcastPlayerState(PlayerData data) {


    }

    @Override
    public void broadcastChunkUpdate(ChunkUpdate chunk) {

    }

    @Override
    public void broadcastWorldObjectUpdate(WorldObjectUpdate objUpdate) {
        if (!isServer) {
            log.warn("Client attempted to broadcast world object update - ignoring");
            return;
        }
        synchronized (pendingObjectUpdates) {
            pendingObjectUpdates.add(objUpdate);
        }
    }

    @Override
    public void tick(float delta) {

    }
}
