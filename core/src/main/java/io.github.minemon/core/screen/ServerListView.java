package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.minemon.multiplayer.model.ServerConnectionConfig;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ServerListView extends Table {
    private static final float ROW_HEIGHT = 80f;
    private static final float ICON_SIZE = 48f;
    private static final float PADDING = 10f;
    private static final String DEFAULT_ICON_PATH = "icons/default-server-icon.png";
    private static final String DEFAULT_MOTD = "No message of the day provided.";

    private final Skin skin;
    private final List<ServerConnectionConfig> servers;
    private final ButtonGroup<TextButton> buttonGroup;

    @Getter
    private ServerConnectionConfig selected;

    public ServerListView(Skin skin, List<ServerConnectionConfig> servers) {
        super(skin);
        this.skin = skin;
        this.servers = new ArrayList<>(servers);
        this.buttonGroup = new ButtonGroup<>();
        buttonGroup.setMaxCheckCount(1);
        buttonGroup.setMinCheckCount(0);
        buttonGroup.setUncheckLast(true);

        setFillParent(true);
        rebuild();
    }

    private void rebuild() {
        clear();
        buttonGroup.clear();



        for (ServerConnectionConfig server : servers) {
            createServerRow(server);
        }

        if (servers.isEmpty()) {
            Label noServersLabel = new Label("No servers added yet.", skin);
            noServersLabel.setColor(Color.LIGHT_GRAY);
            add(noServersLabel).pad(20).center().row();
        }

        pack();
    }

    private void createServerRow(ServerConnectionConfig server) {
        final Color normalColor = new Color(0.2f, 0.2f, 0.2f, 1f);
        final Color hoverColor = new Color(0.3f, 0.3f, 0.3f, 1f);
        final Color selectedColor = new Color(0.4f, 0.6f, 0.9f, 0.7f);

        Table rowTable = new Table(skin);
        rowTable.setBackground(skin.newDrawable("white", normalColor));

        Image icon = createServerIcon(server);
        rowTable.add(icon).size(ICON_SIZE).pad(PADDING);

        Table infoTable = new Table(skin);
        Label nameLabel = new Label(server.getServerName(), skin);
        nameLabel.setColor(Color.WHITE);

        Label motdLabel = new Label(getMotd(server), skin);
        motdLabel.setWrap(true);
        motdLabel.setColor(new Color(0.8f, 0.8f, 0.8f, 1f));

        infoTable.add(nameLabel).left().expandX().row();
        infoTable.add(motdLabel).left().expandX().padTop(5).width(400);

        rowTable.add(infoTable).expandX().fillX().pad(PADDING);

        Table playerCountTable = new Table(skin);
        playerCountTable.setBackground(skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.7f)));
        Label playersLabel = new Label(
                String.format("%d/%d", server.getCurrentPlayers(), server.getMaxPlayers()),
                skin
        );
        playersLabel.setColor(Color.WHITE);
        playerCountTable.add(playersLabel).pad(5);
        rowTable.add(playerCountTable).right().padRight(PADDING);

        TextButton rowButton = new TextButton("", skin);
        rowButton.add(rowTable).expand().fill();

        rowButton.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (!rowButton.isChecked()) {
                    rowTable.setBackground(skin.newDrawable("white", hoverColor));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (!rowButton.isChecked()) {
                    rowTable.setBackground(skin.newDrawable("white", normalColor));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (rowButton.isChecked()) {
                    selected = server;
                    rowTable.setBackground(skin.newDrawable("white", selectedColor));
                } else {
                    if (selected == server) selected = null;
                    rowTable.setBackground(skin.newDrawable("white", normalColor));
                }
            }
        });

        buttonGroup.add(rowButton);

        add(rowButton).expandX().fillX().height(ROW_HEIGHT).padBottom(2).row();
    }

    private Image createServerIcon(ServerConnectionConfig server) {
        String iconPath = server.getIconPath();
        if (iconPath == null || iconPath.trim().isEmpty() || !new File(iconPath).exists()) {
            iconPath = DEFAULT_ICON_PATH;
        }
        return new Image(new Texture(Gdx.files.internal(iconPath)));
    }

    private String getMotd(ServerConnectionConfig server) {
        String motd = server.getMotd();
        return (motd == null || motd.trim().isEmpty()) ? DEFAULT_MOTD : motd;
    }

    public void setServers(List<ServerConnectionConfig> newServers) {
        servers.clear();
        servers.addAll(newServers);
        selected = null;
        rebuild();
    }
}
