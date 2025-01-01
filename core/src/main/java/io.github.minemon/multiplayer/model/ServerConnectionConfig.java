package io.github.minemon.multiplayer.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ServerConnectionConfig {
    private String serverIP = "127.0.0.1";
    private int tcpPort = 54555;
    private int udpPort = 54777;
    private int maxPlayers = 20;
    private String iconPath = "";
    private String motd = "";
    private String serverName = "Default Server";
    private String version = "1.0";
    private String dataDirectory = "worlds";
    private boolean isDefault = false;
    private int currentPlayers = 0;


    private boolean rememberMe = false;
    private String savedUsername = "";
    private String savedPassword = "";

    @Override
    public String toString() {
        return serverName + " (" + serverIP + ":" + tcpPort + ")";
    }
}
