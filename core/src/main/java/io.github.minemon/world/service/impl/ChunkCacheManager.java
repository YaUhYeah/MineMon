package io.github.minemon.world.service.impl;

import io.github.minemon.world.service.WorldService;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChunkCacheManager {
    private final Map<String, Map<ChunkKey, Long>> clientChunks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final int maxChunksPerClient;
    private final long cacheTtl;

    public ChunkCacheManager(
            @Value("${server.chunk.cache.maxPerClient:256}") int maxChunksPerClient,
            @Value("${server.chunk.cache.ttl:300000}") long cacheTtl) {
        this.maxChunksPerClient = maxChunksPerClient;
        this.cacheTtl = cacheTtl;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        startCleanupTask();
    }

    public boolean isChunkCached(String clientId, int chunkX, int chunkY) {
        Map<ChunkKey, Long> clientCache = clientChunks.get(clientId);
        if (clientCache == null) return false;

        ChunkKey key = new ChunkKey(chunkX, chunkY);
        Long cacheTime = clientCache.get(key);
        if (cacheTime == null) return false;

        if (System.currentTimeMillis() - cacheTime > cacheTtl) {
            clientCache.remove(key);
            return false;
        }
        return true;
    }
    private final boolean isServer = false;

    @Autowired
    private WorldService worldService;

    public void cacheChunk(String clientId, int chunkX, int chunkY) {
        
        if (!isServer && worldService.isMultiplayerMode()) {
            return;
        }
        clientChunks.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>())
                .put(new ChunkKey(chunkX, chunkY), System.currentTimeMillis());

        
        Map<ChunkKey, Long> clientCache = clientChunks.get(clientId);
        if (clientCache.size() > maxChunksPerClient) {
            List<ChunkKey> oldestChunks = clientCache.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(clientCache.size() - maxChunksPerClient)
                    .map(Map.Entry::getKey)
                    .toList();

            for (ChunkKey key : oldestChunks) {
                clientCache.remove(key);
            }
        }
    }

    public void removeClient(String clientId) {
        clientChunks.remove(clientId);
        log.debug("Removed chunk cache for client: {}", clientId);
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupCache,
                60, 60, TimeUnit.SECONDS);
    }

    private void cleanupCache() {
        long now = System.currentTimeMillis();
        clientChunks.forEach((clientId, chunks) -> {
            chunks.entrySet().removeIf(entry ->
                    now - entry.getValue() > cacheTtl);
        });
        clientChunks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        log.debug("Completed chunk cache cleanup");
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Data
    @AllArgsConstructor
    private static class ChunkKey {
        private final int x;
        private final int y;
    }
}