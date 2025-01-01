package io.github.minemon.chat.commands;

import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.service.PlayerService;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void execute(String args, PlayerService playerService, ChatService chatService, MultiplayerClient multiplayerClient) {
        PlayerData player = playerService.getPlayerData();
        if (player == null) {
            chatService.addSystemMessage("Error: Player not found");
            return;
        }
        player.setX(0);
        player.setY(0);
        playerService.setPosition(0,0);

        if (multiplayerClient.isConnected()) {
            multiplayerClient.sendPlayerMove(player.getX(), player.getY(), player.isWantsToRun(), player.isMoving(), player.getDirection().name().toLowerCase());
        }
        chatService.addSystemMessage("Teleported to spawn point!");
    }
}
