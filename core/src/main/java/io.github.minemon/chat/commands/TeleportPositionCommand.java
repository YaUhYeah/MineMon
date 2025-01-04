package io.github.minemon.chat.commands;

import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.service.ChunkLoadingManager;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TeleportPositionCommand implements Command {

    @Autowired
    private ChunkLoadingManager chunkLoadingManager;

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "teleports to position";
    }

    @Override
    public String getUsage() {
        return "/tp <tileX> <tileY>";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, PlayerService playerService, ChatService chatService, MultiplayerClient multiplayerClient) {
        String[] argsArray = args.split(" ");
        PlayerData player = playerService.getPlayerData();
        try {
            if (player == null) {
                chatService.addSystemMessage("Error: Player not found");
                return;
            }

            if (argsArray.length != 2) {
                chatService.addSystemMessage("Invalid arguments, use: " + getUsage());
                return;
            }

            int tileX = Integer.parseInt(argsArray[0]);
            int tileY = Integer.parseInt(argsArray[1]);

            chunkLoadingManager.preloadChunksAroundPosition(tileX, tileY);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            player.setX(tileX * 32);
            player.setY(tileY * 32);
            playerService.setPosition(tileX, tileY);

            if (multiplayerClient.isConnected()) {
                multiplayerClient.sendPlayerMove(
                    player.getX(),
                    player.getY(),
                    player.isWantsToRun(),
                    false,
                    player.getDirection().name().toLowerCase()
                );
            }

            chatService.addSystemMessage("Teleported to (" + tileX + ", " + tileY + ")");
        } catch (Exception e) {
            log.error("Error executing tp command: " + e.getMessage());
        }
    }
}
