package io.github.minemon.core.ui;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import io.github.minemon.multiplayer.model.ServerConnectionConfig;
import java.util.function.Consumer;

public class ServerConfigDialog extends Dialog {
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final String DEFAULT_IP = "127.0.0.1";
    private static final int DEFAULT_TCP_PORT = 54555;
    private static final int DEFAULT_UDP_PORT = 54777;

    private final DialogFactory dialogFactory;
    private final Consumer<ServerConnectionConfig> onSave;
    private final ServerConnectionConfig existingConfig;

    private TextField nameField;
    private TextField ipField;
    private TextField tcpField;
    private TextField udpField;

    public ServerConfigDialog(Skin skin, DialogFactory dialogFactory,
                              ServerConnectionConfig existingConfig,
                              Consumer<ServerConnectionConfig> onSave) {
        super(existingConfig == null ? "Add Server" : "Edit Server", skin);
        this.dialogFactory = dialogFactory;
        this.existingConfig = existingConfig;
        this.onSave = onSave;

        createContent();
        createButtons();
    }

    private void createContent() {
        Table content = new Table(getSkin());
        content.pad(20);


        content.add("Server Name:").left().padRight(10);
        nameField = new TextField(getExistingValue(ServerConnectionConfig::getServerName, ""), getSkin());
        content.add(nameField).width(250).left().row();


        content.add("IP Address:").left().padRight(10).padTop(10);
        ipField = new TextField(getExistingValue(ServerConnectionConfig::getServerIP, DEFAULT_IP), getSkin());
        content.add(ipField).width(250).left().row();


        content.add("TCP Port:").left().padRight(10).padTop(10);
        tcpField = new TextField(getExistingValue(
                config -> String.valueOf(config.getTcpPort()),
                String.valueOf(DEFAULT_TCP_PORT)),
                getSkin());
        content.add(tcpField).width(250).left().row();


        content.add("UDP Port:").left().padRight(10).padTop(10);
        udpField = new TextField(getExistingValue(
                config -> String.valueOf(config.getUdpPort()),
                String.valueOf(DEFAULT_UDP_PORT)),
                getSkin());
        content.add(udpField).width(250).left().row();

        getContentTable().add(content);
    }

    private void createButtons() {
        TextButton saveButton = new TextButton("Save", getSkin());
        TextButton cancelButton = new TextButton("Cancel", getSkin());

        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleSave();
            }
        });

        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });

        button(saveButton);
        button(cancelButton);
    }

    private void handleSave() {

        if (nameField.getText().trim().isEmpty()) {
            dialogFactory.showWarning("Invalid Input", "Server name cannot be empty");
            return;
        }

        if (ipField.getText().trim().isEmpty()) {
            dialogFactory.showWarning("Invalid Input", "IP address cannot be empty");
            return;
        }

        try {
            int tcpPort = Integer.parseInt(tcpField.getText().trim());
            int udpPort = Integer.parseInt(udpField.getText().trim());

            if (!isValidPort(tcpPort) || !isValidPort(udpPort)) {
                dialogFactory.showWarning("Invalid Input",
                        "Ports must be between " + MIN_PORT + " and " + MAX_PORT);
                return;
            }

            ServerConnectionConfig config = existingConfig != null ?
                    existingConfig : new ServerConnectionConfig();

            config.setServerName(nameField.getText().trim());
            config.setServerIP(ipField.getText().trim());
            config.setTcpPort(tcpPort);
            config.setUdpPort(udpPort);

            if (existingConfig == null) {

                config.setMotd("Welcome to " + config.getServerName());
                config.setMaxPlayers(20);
                config.setIconPath("");
                config.setDefault(false);
            }

            onSave.accept(config);
            hide();

        } catch (NumberFormatException e) {
            dialogFactory.showWarning("Invalid Input", "Port numbers must be valid integers");
        }
    }

    private boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    private <T> String getExistingValue(java.util.function.Function<ServerConnectionConfig, T> getter,
                                        String defaultValue) {
        if (existingConfig != null) {
            T value = getter.apply(existingConfig);
            return value != null ? value.toString() : defaultValue;
        }
        return defaultValue;
    }
}