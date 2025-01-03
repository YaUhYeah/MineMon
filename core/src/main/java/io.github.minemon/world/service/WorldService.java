package io.github.minemon.world.service;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.world.model.ChunkData;
import io.github.minemon.world.model.WorldData;
import io.github.minemon.world.model.WorldObject;

import java.util.List;
import java.util.Map;

public interface WorldService {
    void clearWorldData();
    void initIfNeeded();
    ChunkData loadOrGenerateChunk(int chunkX, int chunkY);
    void update(float delta);
    void handleDisconnect();
    WorldData getWorldData();
    boolean isMultiplayerMode();
    void setMultiplayerMode(boolean multiplayer);
    void loadOrReplaceChunkData(int chunkX, int chunkY, int[][] tiles, List<WorldObject> objects);
    void updateWorldObjectState(WorldObjectUpdate update);
    TileManager getTileManager();
    boolean createWorld(String worldName, long seed);
    void loadWorld(String worldName);
    List<WorldObject> getVisibleObjects(Rectangle viewBounds);
    OrthographicCamera getCamera();
    boolean isChunkLoaded(Vector2 chunkPos);
    void loadChunk(Vector2 chunkPos);
    void setCamera(OrthographicCamera camera);
    Map<String, ChunkData> getVisibleChunks(Rectangle viewBounds);
    void saveWorldData();
    void loadWorldData();

    List<String> getAvailableWorlds();
    void deleteWorld(String worldName);


    void setPlayerData(PlayerData playerData);
    PlayerData getPlayerData(String username);


    int[][] getChunkTiles(int chunkX, int chunkY);
    void regenerateChunk(int chunkX, int chunkY);


    void generateWorldThumbnail(String worldName);
}
