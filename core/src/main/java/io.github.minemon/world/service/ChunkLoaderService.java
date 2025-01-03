package io.github.minemon.world.service;

import com.badlogic.gdx.math.Vector2;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class ChunkLoaderService {
    private static final int VISIBLE_RADIUS = 2;
    private static final int PRELOAD_RADIUS = 4;

    private final Map<Vector2, Float> chunkFadeStates = new ConcurrentHashMap<>();
    private final Set<Vector2> preloadedChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final WorldService worldService;
    private final ExecutorService executorService;
    private final Map<Vector2, CompletableFuture<Void>> loadingChunks = new ConcurrentHashMap<>();
    public ChunkLoaderService(WorldService worldService) {
        this.worldService = worldService;
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public void updatePlayerPosition(float playerX, float playerY) {
        int chunkX = (int) Math.floor(playerX / (16 * 32));
        int chunkY = (int) Math.floor(playerY / (16 * 32));


        Set<Vector2> requiredChunks = new HashSet<>();
        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dy = -PRELOAD_RADIUS; dy <= PRELOAD_RADIUS; dy++) {
                requiredChunks.add(new Vector2(chunkX + dx, chunkY + dy));
            }
        }


        for (Vector2 chunkPos : requiredChunks) {
            if (!preloadedChunks.contains(chunkPos)) {
                preloadChunk(chunkPos);
            }
        }


        for (int dx = -VISIBLE_RADIUS; dx <= VISIBLE_RADIUS; dx++) {
            for (int dy = -VISIBLE_RADIUS; dy <= VISIBLE_RADIUS; dy++) {
                Vector2 visibleChunk = new Vector2(chunkX + dx, chunkY + dy);
                if (!chunkFadeStates.containsKey(visibleChunk)) {
                    chunkFadeStates.put(visibleChunk, 0f);
                }
            }
        }
    }

    private void preloadChunk(Vector2 chunkPos) {
        if (!loadingChunks.containsKey(chunkPos) && !worldService.isChunkLoaded(chunkPos)) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                worldService.loadChunk(chunkPos);
                preloadedChunks.add(chunkPos);
            }, executorService).whenComplete((result, ex) -> {
                loadingChunks.remove(chunkPos);
            });
            loadingChunks.put(chunkPos, future);
        }
    }
    private void preloadChunks(int centerX, int centerY) {
        Set<Vector2> requiredChunks = new HashSet<>();


        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dy = -PRELOAD_RADIUS; dy <= PRELOAD_RADIUS; dy++) {
                requiredChunks.add(new Vector2(centerX + dx, centerY + dy));
            }
        }


        for (Vector2 chunkPos : requiredChunks) {
            if (!loadingChunks.containsKey(chunkPos) && !worldService.isChunkLoaded(chunkPos)) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    worldService.loadChunk(chunkPos);
                }, executorService).whenComplete((result, ex) -> {
                    loadingChunks.remove(chunkPos);
                });
                loadingChunks.put(chunkPos, future);
            }
        }
    }

    public void dispose() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
