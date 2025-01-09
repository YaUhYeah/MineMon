package io.github.minemon.world;

import com.badlogic.gdx.utils.Json;
import io.github.minemon.core.service.FileAccessService;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SpawnService {
    private static final String SPAWN_FILE = "spawn.json";
    private final FileAccessService fileAccessService;
    private final WorldService worldService;
    private SpawnPoint defaultSpawn;
    private final Json json;

    @Autowired
    public SpawnService(FileAccessService fileAccessService, WorldService worldService) {
        this.fileAccessService = fileAccessService;
        this.worldService = worldService;
        this.json = new Json();
        this.defaultSpawn = new SpawnPoint(0, 0);
        loadSpawn();
    }

    public void setSpawn(float x, float y) {
      SpawnPoint newSpawn = new SpawnPoint(x, y);

        if (worldService.isMultiplayerMode()) {
            
            this.defaultSpawn = newSpawn;
        } else {
            
            this.defaultSpawn = newSpawn;
            saveSpawn();
        }
    }

    public SpawnPoint getSpawn() {
        return defaultSpawn;
    }

    private void loadSpawn() {
        try {
            String worldName = worldService.getWorldData().getWorldName();
            if (worldName == null) return;

            String path = "save/worlds/" + worldName + "/" + SPAWN_FILE;
            if (fileAccessService.exists(path)) {
                String content = fileAccessService.readFile(path);
                SpawnPoint loaded = json.fromJson(SpawnPoint.class, content);
                if (loaded != null) {
                    this.defaultSpawn = loaded;
                }
            }
        } catch (Exception e) {
            log.error("Failed to load spawn point: {}", e.getMessage());
        }
    }

    private void saveSpawn() {
        try {
            String worldName = worldService.getWorldData().getWorldName();
            if (worldName == null) return;

            String path = "save/worlds/" + worldName + "/" + SPAWN_FILE;
            String content = json.toJson(defaultSpawn);
            fileAccessService.writeFile(path, content);
        } catch (Exception e) {
            log.error("Failed to save spawn point: {}", e.getMessage());
        }
    }
}
