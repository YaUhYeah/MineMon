package io.github.minemon.chat.commands;

import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.SpawnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SetSpawnCommand implements Command {

    @Autowired
    private SpawnService spawnService;

    @Override
    public String getName() {
        return "setspawn";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public String getDescription() {
        return "Sets spawn point to current location or specified coordinates";
    }

    @Override
    public String getUsage() {
        return "/setspawn [x] [y]";
    }

    @Override
    public boolean isMultiplayerOnly() {
        return false;
    }

    @Override
    public void execute(String args, PlayerService playerService,
                        ChatService chatService, MultiplayerClient multiplayerClient) {

        float x, y;

        if (args == null || args.trim().isEmpty()) {
            
            PlayerData player = playerService.getPlayerData();
            if (player == null) {
                chatService.addSystemMessage("Error: Player not found");
                return;
            }
            x = player.getX();
            y = player.getY();
        } else {
            
            String[] parts = args.trim().split("\\s+");
            if (parts.length != 2) {
                chatService.addSystemMessage("Usage: " + getUsage());
                return;
            }

            try {
                x = Float.parseFloat(parts[0]);
                y = Float.parseFloat(parts[1]);
            } catch (NumberFormatException e) {
                chatService.addSystemMessage("Invalid coordinates. Use numbers only.");
                return;
            }
        }

        
        spawnService.setSpawn(x, y);

        
        String message = String.format("Spawn point set to (%.1f, %.1f)", x, y);
        chatService.addSystemMessage(message);

        
        if (multiplayerClient.isConnected()) {
            
            log.debug("Spawn point updated in multiplayer mode");
        }
    }
}
