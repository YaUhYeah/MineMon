package io.github.minemon.world.service;

import com.badlogic.gdx.math.Vector2;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ChunkPreloaderService {
    // Increase the preload radius to ensure chunks are loaded before visible
    private static final int PRELOAD_RADIUS = 5;  // Increased from 3
    private static final int LOAD_BUFFER = 2;     // Extra buffer around visible area
    private final WorldService worldService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private float lastPreloadX = Float.MIN_VALUE;
    private float lastPreloadY = Float.MIN_VALUE;
    @Autowired
    @Lazy
    private MultiplayerClient client;
    public ChunkPreloaderService(WorldService worldService) {
        this.worldService = worldService;
    }

    public void dispose() {
        executor.shutdownNow();
    }

    public void preloadAround(float playerX, float playerY) {
        // Preload more frequently - reduce distance check
        if (Math.abs(playerX - lastPreloadX) < 64 && Math.abs(playerY - lastPreloadY) < 64) {
            return;
        }
        lastPreloadX = playerX;
        lastPreloadY = playerY;

        int tileX = (int) (playerX / 32);
        int tileY = (int) (playerY / 32);
        int playerChunkX = tileX / 16;
        int playerChunkY = tileY / 16;

        executor.submit(() -> {
            // Preload in a larger area
            for (int cx = playerChunkX - PRELOAD_RADIUS - LOAD_BUFFER;
                 cx <= playerChunkX + PRELOAD_RADIUS + LOAD_BUFFER; cx++) {
                for (int cy = playerChunkY - PRELOAD_RADIUS - LOAD_BUFFER;
                     cy <= playerChunkY + PRELOAD_RADIUS + LOAD_BUFFER; cy++) {
                    Vector2 chunkPos = new Vector2(cx, cy);
                    if (!worldService.isChunkLoaded(chunkPos)) {
                        if (worldService.isMultiplayerMode()) {
                            // In multiplayer, request chunks from server
                            requestChunkFromServer(cx, cy);
                        } else {
                            // In singleplayer, load/generate immediately
                            worldService.loadChunk(chunkPos);
                        }
                    }
                }
            }
            log.debug("Preloaded chunks around player ({},{})", playerX, playerY);
        });
    }

    private void requestChunkFromServer(int chunkX, int chunkY) {
        if (worldService.isMultiplayerMode()) {
            if (client != null && client.isConnected()) {
                client.requestChunk(chunkX, chunkY);
            }
        }
    }
}
