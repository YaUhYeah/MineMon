package io.github.minemon.chat.commands;

import io.github.minemon.chat.service.ChatService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.service.PlayerService;

public interface Command {
    String getName();
    String[] getAliases();
    String getDescription();
    String getUsage();
    boolean isMultiplayerOnly();

    
    void execute(String args, PlayerService playerService, ChatService chatService, MultiplayerClient multiplayerClient);
}
