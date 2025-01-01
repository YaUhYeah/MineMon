package io.github.minemon.world.service;

import com.badlogic.gdx.math.Vector2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
@Slf4j
public class ChunkPreloaderService {
    private static final int PRELOAD_RADIUS = 3;

    private final WorldService worldService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private float lastPreloadX = Float.MIN_VALUE;
    private float lastPreloadY = Float.MIN_VALUE;

    public ChunkPreloaderService(WorldService worldService) {
        this.worldService = worldService;
    }

    public void preloadAround(float playerX, float playerY) {

        if (Math.abs(playerX - lastPreloadX) < 128 && Math.abs(playerY - lastPreloadY) < 128) {
            return;
        }
        lastPreloadX = playerX;
        lastPreloadY = playerY;

        int tileX = (int)(playerX / 32);
        int tileY = (int)(playerY / 32);
        int playerChunkX = tileX / 16;
        int playerChunkY = tileY / 16;

        executor.submit(() -> {
            for (int cx = playerChunkX - PRELOAD_RADIUS; cx <= playerChunkX + PRELOAD_RADIUS; cx++) {
                for (int cy = playerChunkY - PRELOAD_RADIUS; cy <= playerChunkY + PRELOAD_RADIUS; cy++) {
                    Vector2 chunkPos = new Vector2(cx, cy);
                    if (!worldService.isChunkLoaded(chunkPos)) {
                        worldService.loadChunk(chunkPos);
                    }
                }
            }
            log.debug("Preloaded chunks around player ({},{})", playerX, playerY);
        });
    }

    public void dispose() {
        executor.shutdownNow();
    }
}
