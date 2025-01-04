package io.github.minemon.chat.commands;

import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.service.ChunkLoadingManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpawnCommand implements Command {
    private static final float SPAWN_X = 0f;
    private static final float SPAWN_Y = 0f;
    private static final long CHUNK_LOAD_TIMEOUT = 2000; // 2 seconds max wait

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String[] getAliases() { return new String[0]; }

    @Override
    public String getDescription() { return "Teleports player to spawn point"; }

    @Override
    public String getUsage() { return "/spawn"; }

    @Override
    public boolean isMultiplayerOnly() { return false; }

    @Autowired
    private ChunkLoadingManager chunkLoadingManager;

    @Override
    public void execute(String args, PlayerService playerService,
                        ChatService chatService, MultiplayerClient multiplayerClient) {

        PlayerData player = playerService.getPlayerData();
        if (player == null) {
            chatService.addSystemMessage("Error: Player not found");
            return;
        }

        chatService.addSystemMessage("Preparing spawn area...");

        // Start preloading chunks
        chunkLoadingManager.preloadChunksAroundPosition(SPAWN_X, SPAWN_Y);

        // Give chunks a moment to start loading
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Set position
        player.setX(SPAWN_X);
        player.setY(SPAWN_Y);
        playerService.setPosition(0, 0);

        // Sync with server if needed
        if (multiplayerClient.isConnected()) {
            multiplayerClient.sendPlayerMove(
                player.getX(),
                player.getY(),
                player.isWantsToRun(),
                false,
                player.getDirection().name().toLowerCase()
            );
        }

        chatService.addSystemMessage("Teleported to spawn point!");
    }
}
