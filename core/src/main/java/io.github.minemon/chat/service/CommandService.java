package io.github.minemon.chat.service;

import io.github.minemon.chat.commands.Command;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.service.PlayerService;

public interface CommandService {
    void registerCommand(Command command);
    boolean executeCommand(String name, String args, PlayerService playerService, ChatService chatService, MultiplayerClient multiplayerClient);
}
