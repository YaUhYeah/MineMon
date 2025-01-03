package io.github.minemon.chat.commands;

import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpawnCommand implements Command {
    @Override
    public String getName() { return "spawn"; }
    @Override
    public String[] getAliases() { return new String[0]; }
    @Override
    public String getDescription() { return "Teleports player to spawn."; }
    @Override
    public String getUsage() { return "/spawn"; }
    @Override
    public boolean isMultiplayerOnly() { return false; }

    @Autowired
    private WorldService worldService;

    @Override
    public void execute(
        String args,
        PlayerService playerService,
        ChatService chatService,
        MultiplayerClient multiplayerClient
    ) {
        PlayerData player = playerService.getPlayerData();
        if (player == null) {
            chatService.addSystemMessage("Error: Player not found");
            return;
        }

        // Example spawn coords: (0, 0)
        float spawnX = 0;
        float spawnY = 0;

        // 1) Force chunk load around the spawn BEFORE we actually move
        //    (optional if you want to load them first)
        //    This can reduce pop-in, but might slow the command a bit in singleplayer
        //    so you can do it after if you prefer.
        worldService.preloadChunksAroundPosition(spawnX, spawnY);

        // 2) Now actually move the player
        player.setX(spawnX);
        player.setY(spawnY);
        playerService.setPosition((int)spawnX, (int)spawnY);

        // 3) Send move to server if in multiplayer
        if (multiplayerClient.isConnected()) {
            multiplayerClient.sendPlayerMove(
                player.getX(),
                player.getY(),
                player.isWantsToRun(),
                player.isMoving(),
                player.getDirection().name().toLowerCase()
            );
        }

        // 4) Optionally do a forced chunk load again AFTER we set the position
        //    So that the local player for sure sees them right away
        worldService.forceLoadChunksAt(player.getX(), player.getY());

        chatService.addSystemMessage("Teleported to spawn point!");
    }
}
