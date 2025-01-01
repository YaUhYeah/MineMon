package io.github.minemon.multiplayer.service.impl;

import com.badlogic.gdx.utils.Json;
import io.github.minemon.multiplayer.model.ServerConnectionConfig;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.core.service.FileAccessService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Primary
@Profile("client")
public class ServerConnectionServiceImpl implements ServerConnectionService {

    private final List<ServerConnectionConfig> serverList = new ArrayList<>();
    private final FileAccessService fileAccessService;
    private ServerConnectionConfig currentConfig;

    public ServerConnectionServiceImpl(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
    }

    @PostConstruct
    public void init() {
        loadServersFromFile();

        if (serverList.isEmpty()) {
            ServerConnectionConfig defaultServer = new ServerConnectionConfig();
            defaultServer.setServerName("Localhost");
            defaultServer.setServerIP("127.0.0.1");
            defaultServer.setTcpPort(54555);
            defaultServer.setUdpPort(54777);
            defaultServer.setMotd("Welcome!");
            defaultServer.setMaxPlayers(20);
            defaultServer.setRememberMe(false);
            serverList.add(defaultServer);
        }

        currentConfig = serverList.get(0);
    }
    @Override
    public void saveConfig(ServerConnectionConfig config) {
        ensureInitialized();

        boolean found = false;
        for (int i = 0; i < serverList.size(); i++) {
            ServerConnectionConfig s = serverList.get(i);
            if (s.getServerName().equals(config.getServerName()) && s.getServerIP().equals(config.getServerIP())) {
                serverList.set(i, config);
                found = true;
                break;
            }
        }

        if (!found) {
            serverList.add(config);
        }

        currentConfig = config;
        saveServersToFile();
        System.out.println("Server configuration saved: " + config);
    }

    @Override
    public ServerConnectionConfig loadConfig() {
        ensureInitialized();
        return currentConfig;
    }

    @Override
    public List<ServerConnectionConfig> listServers() {
        ensureInitialized();
        return new ArrayList<>(serverList);
    }

    @Override
    public void addServer(ServerConnectionConfig config) {
        ensureInitialized();
        serverList.add(config);
        saveServersToFile();
        System.out.println("Server added: " + config);
    }

    @Override
    public void deleteServer(ServerConnectionConfig config) {
        ensureInitialized();
        serverList.removeIf(s ->
                s.getServerName().equals(config.getServerName()) &&
                        s.getServerIP().equals(config.getServerIP())
        );
        saveServersToFile();
        System.out.println("Server deleted: " + config);
    }

    private void ensureInitialized() {
        if (currentConfig == null) {
            throw new IllegalStateException("ServerConnectionService is not initialized.");
        }
    }

    private void saveServersToFile() {
        Json json = new Json();
        String data = json.toJson(serverList);
        fileAccessService.writeFile("data/servers.json", data);
    }

    @SuppressWarnings("unchecked")
    private void loadServersFromFile() {
        if (fileAccessService.exists("data/servers.json")) {
            Json json = new Json();
            List<ServerConnectionConfig> loaded = json.fromJson(ArrayList.class, ServerConnectionConfig.class, fileAccessService.readFile("data/servers.json"));
            serverList.clear();
            if (loaded != null) {
                serverList.addAll(loaded);
            }
        }
    }
}
