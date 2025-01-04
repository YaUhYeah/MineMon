package io.github.minemon.chat.config;

import io.github.minemon.chat.commands.SetSpawnCommand;
import io.github.minemon.chat.commands.SpawnCommand;
import io.github.minemon.chat.service.CommandService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Autowired
    private CommandService commandService;

    @Autowired
    private SpawnCommand spawnCommand;

    @Autowired
    private SetSpawnCommand setSpawnCommand;

    @PostConstruct
    public void registerCommands() {
        commandService.registerCommand(spawnCommand);
        commandService.registerCommand(setSpawnCommand);
    }
}
