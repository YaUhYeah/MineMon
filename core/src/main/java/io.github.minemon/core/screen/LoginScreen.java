package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.core.service.UiService;
import io.github.minemon.core.ui.DialogCloseListener;
import io.github.minemon.core.ui.ServerConfigDialog;
import io.github.minemon.multiplayer.model.ServerConnectionConfig;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.multiplayer.service.ServerConnectionService;
import io.github.minemon.multiplayer.service.impl.ClientConnectionManager;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Scope("prototype")
public class LoginScreen implements Screen,
    MultiplayerClient.LoginResponseListener,
    MultiplayerClient.CreateUserResponseListener {

    
    private static final float MIN_WORLD_WIDTH = 960f;
    private static final float MIN_WORLD_HEIGHT = 540f;
    private static final float MAX_WORLD_WIDTH = 1920f;
    private static final float MAX_WORLD_HEIGHT = 1080f;

    private static final String BACKGROUND_PATH = "Textures/UI/ethereal.png";

    private final AudioService audioService;
    private final ScreenManager screenManager;
    private final ServerConnectionService serverConnectionService;
    private final MultiplayerClient multiplayerClient;
    private final UiService uiService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private WorldService worldService;

    @Autowired
    private ClientConnectionManager connectionManager;

    private Stage stage;
    private Skin skin;
    private Table root;
    private ServerListView serverListView;
    private Dialog connectingDialog;
    private Dialog loginDialog;

    @Autowired
    public LoginScreen(
        AudioService audioService,
        ScreenManager screenManager,
        ServerConnectionService serverConnectionService,
        MultiplayerClient multiplayerClient,
        UiService uiService) {
        this.audioService = audioService;
        this.screenManager = screenManager;
        this.serverConnectionService = serverConnectionService;
        this.multiplayerClient = multiplayerClient;
        this.uiService = uiService;
    }

    @Override
    public void show() {
        
        stage = new Stage(
            new ExtendViewport(
                MIN_WORLD_WIDTH, MIN_WORLD_HEIGHT,
                MAX_WORLD_WIDTH, MAX_WORLD_HEIGHT
            )
        );

        
        Gdx.input.setCatchKey(Input.Keys.BACK, true);

        uiService.initialize();
        uiService.getDialogFactory().setStage(stage);
        skin = uiService.getSkin();
        Gdx.input.setInputProcessor(stage);

        audioService.playMenuMusic();

        createUI();
        setupInputHandling();
    }

    private void createUI() {
        setBackground();

        
        root = new Table(skin);
        root.setFillParent(true);
        stage.addActor(root);

        
        float screenWidth = stage.getViewport().getWorldWidth();
        float scaleFactor = screenWidth / 1280f; 

        
        Table topBar = new Table(skin);
        topBar.setBackground(uiService.getStyleFactory().createPanelBackground());
        topBar.pad(10 * scaleFactor);  

        TextButton backButton = createSimpleButton("Back to Menu", this::handleBack, scaleFactor);
        topBar.add(backButton).left().padRight(20 * scaleFactor);

        Label titleLabel = uiService.getStyleFactory().createTitleLabel("PokéMeetup Server List", skin);
        
        titleLabel.setFontScale(1.2f * scaleFactor);

        topBar.add(titleLabel).expandX().center();
        topBar.add().width(100 * scaleFactor);

        root.add(topBar).expandX().fillX().row();

        
        Table headerRow = new Table(skin);
        headerRow.setBackground(skin.newDrawable("white", 0.2f, 0.2f, 0.2f, 0.8f));
        headerRow.pad(10 * scaleFactor);

        Label nameHeader = new Label("Server Name", skin);
        nameHeader.setColor(Color.GOLD);
        nameHeader.setFontScale(scaleFactor);

        Label playersHeader = new Label("Players", skin);
        playersHeader.setColor(Color.GOLD);
        playersHeader.setFontScale(scaleFactor);

        headerRow.add(nameHeader).expandX().left().padLeft(80 * scaleFactor);
        headerRow.add(playersHeader).width(100 * scaleFactor).right().padRight(10 * scaleFactor);

        root.add(headerRow).expandX().fillX().height(40 * scaleFactor).row();

        
        serverListView = new ServerListView(skin, loadServers());
        ScrollPane scrollPane = new ScrollPane(serverListView, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setForceScroll(false, true);
        scrollPane.setSmoothScrolling(true);

        Table serverContainer = new Table(skin);
        serverContainer.setBackground(uiService.getStyleFactory().createPanelBackground());
        serverContainer.add(scrollPane).expand().fill().pad(15 * scaleFactor);

        root.add(serverContainer).expand().fill().pad(20 * scaleFactor).row();

        
        Table buttonPanel = new Table(skin);
        buttonPanel.setBackground(uiService.getStyleFactory().createPanelBackground());
        buttonPanel.pad(10 * scaleFactor);

        float buttonWidth = 150 * scaleFactor;
        float buttonHeight = 50 * scaleFactor;

        TextButton joinButton = createSimpleButton("Join Server", this::handleJoinServer, scaleFactor);
        TextButton addButton = createSimpleButton("Add Server", this::handleAddServer, scaleFactor);
        TextButton editButton = createSimpleButton("Edit Server", this::handleEditServer, scaleFactor);
        TextButton deleteButton = createSimpleButton("Delete Server", this::handleDeleteServer, scaleFactor);

        buttonPanel.add(joinButton).width(buttonWidth).height(buttonHeight).pad(10 * scaleFactor);
        buttonPanel.add(addButton).width(buttonWidth).height(buttonHeight).pad(10 * scaleFactor);
        buttonPanel.add(editButton).width(buttonWidth).height(buttonHeight).pad(10 * scaleFactor);
        buttonPanel.add(deleteButton).width(buttonWidth).height(buttonHeight).pad(10 * scaleFactor);

        root.add(buttonPanel).expandX().fillX().padBottom(20 * scaleFactor);
    }

    private void setBackground() {
        Texture bgTexture = new Texture(Gdx.files.internal(BACKGROUND_PATH));
        Image backgroundImage = new Image(new TextureRegionDrawable(bgTexture));
        backgroundImage.setFillParent(true);
        backgroundImage.setScaling(Scaling.fill);
        backgroundImage.setTouchable(Touchable.disabled);
        stage.addActor(backgroundImage);
    }

    private TextButton createSimpleButton(String text, Runnable action, float scaleFactor) {
        TextButton button = uiService.getStyleFactory().createGameButton(text, skin);

        final Color NORMAL_COLOR = Color.WHITE;
        final Color HOVER_COLOR = new Color(0.9f, 0.9f, 0.9f, 1f);
        final Color PRESSED_COLOR = new Color(0.8f, 0.8f, 0.8f, 1f);

        button.setColor(NORMAL_COLOR);
        button.getLabel().setFontScale(MathUtils.clamp(1f * scaleFactor, 0.8f, 2f));

        button.addListener(new InputListener() {
            boolean pressed = false;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int buttonCode) {
                button.setColor(PRESSED_COLOR);
                pressed = true;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int buttonCode) {
                if (pressed) {
                    if (x >= 0 && x <= button.getWidth() && y >= 0 && y <= button.getHeight()) {
                        action.run();
                        button.setColor(HOVER_COLOR);
                    } else {
                        button.setColor(NORMAL_COLOR);
                    }
                    pressed = false;
                }
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (!pressed) {
                    button.setColor(HOVER_COLOR);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (!pressed) {
                    button.setColor(NORMAL_COLOR);
                }
            }
        });

        return button;
    }

    private void handleBack() {
        
        screenManager.goBack();
    }

    private void handleJoinServer() {
        ServerConnectionConfig selected = serverListView.getSelected();
        if (selected == null) {
            uiService.getDialogFactory().showWarning("No Server Selected", "Please select a server to join.");
            return;
        }
        showLoginDialog(selected);
    }

    private void showLoginDialog(ServerConnectionConfig config) {
        
        try {
            connectionManager.releaseInstanceLock();
            if (!connectionManager.acquireInstanceLock()) {
                uiService.getDialogFactory().showError(
                    "Connection Error",
                    "Another instance of the game is already running on this machine."
                );
                return;
            }
        } catch (Exception e) {
            log.error("Lock error: {}", e.getMessage());
            uiService.getDialogFactory().showError(
                "Connection Error",
                "Failed to establish connection lock. Please restart the game."
            );
            return;
        }

        if (loginDialog != null && loginDialog.isVisible()) {
            loginDialog.hide();
        }

        
        if (!connectionManager.acquireInstanceLock()) {
            uiService.getDialogFactory().showError("Connection Error",
                "Another instance of the game is already running on this machine.");
            return;
        }

        if (loginDialog != null && loginDialog.isVisible()) {
            loginDialog.hide();
        }

        String prefillUsername = config.isRememberMe() ? config.getSavedUsername() : "";
        String prefillPassword = config.isRememberMe() ? config.getSavedPassword() : "";

        Window.WindowStyle windowStyle = skin.get(Window.WindowStyle.class);
        loginDialog = new Dialog("Login to " + config.getServerName(), windowStyle);
        loginDialog.pad(20);

        TextField usernameField = new TextField(prefillUsername, skin);
        usernameField.setMessageText("Username");

        TextField passwordField = new TextField(prefillPassword, skin);
        passwordField.setMessageText("Password");
        passwordField.setPasswordCharacter('*');
        passwordField.setPasswordMode(true);

        CheckBox rememberMeCheck = new CheckBox(" Remember Me", skin);
        rememberMeCheck.setChecked(config.isRememberMe());

        Table contentTable = loginDialog.getContentTable();
        contentTable.add(new Label("Username:", skin)).left().pad(5);
        contentTable.row();
        contentTable.add(usernameField).width(200).pad(5);
        contentTable.row();
        contentTable.add(new Label("Password:", skin)).left().pad(5);
        contentTable.row();
        contentTable.add(passwordField).width(200).pad(5);
        contentTable.row();
        contentTable.add(rememberMeCheck).pad(5);

        TextButton loginButton = new TextButton("Login", skin);
        TextButton createButton = new TextButton("Create Account", skin);
        TextButton cancelButton = new TextButton("Cancel", skin);

        loginButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText();
                boolean rm = rememberMeCheck.isChecked();
                if (user.isEmpty() || pass.isEmpty()) {
                    uiService.getDialogFactory().showWarning("Invalid Input",
                        "Username and password cannot be empty.");
                    return;
                }

                config.setRememberMe(rm);
                if (rm) {
                    config.setSavedUsername(user);
                    config.setSavedPassword(pass);
                } else {
                    config.setSavedUsername("");
                    config.setSavedPassword("");
                }
                serverConnectionService.saveConfig(config);

                loginDialog.hide();
                if (!multiplayerClient.isConnected()) {
                    multiplayerClient.setPendingLoginRequest(() -> multiplayerClient.login(user, pass));
                    connectToServer(config);
                } else {
                    multiplayerClient.login(user, pass);
                }
            }
        });

        
        loginDialog.addListener((DialogCloseListener) () -> {
            if (!multiplayerClient.isConnected()) {
                connectionManager.releaseInstanceLock();
            }
        });

        createButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showCreateAccountDialog(config);
                loginDialog.hide();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                loginDialog.hide();
                connectionManager.releaseInstanceLock();
            }
        });

        loginDialog.button(loginButton);
        loginDialog.button(createButton);
        loginDialog.button(cancelButton);

        loginDialog.show(stage);
    }

    private void showCreateAccountDialog(ServerConnectionConfig config) {
        Dialog createDialog = new Dialog("Create Account for " + config.getServerName(), skin);
        createDialog.pad(20);

        TextField usernameField = new TextField("", skin);
        usernameField.setMessageText("Username");
        TextField passwordField = new TextField("", skin);
        passwordField.setMessageText("Password");
        passwordField.setPasswordCharacter('*');
        passwordField.setPasswordMode(true);

        TextField confirmField = new TextField("", skin);
        confirmField.setMessageText("Confirm Password");
        confirmField.setPasswordCharacter('*');
        confirmField.setPasswordMode(true);

        Table contentTable = createDialog.getContentTable();
        contentTable.add(new Label("Username:", skin)).left().pad(5);
        contentTable.row();
        contentTable.add(usernameField).width(200).pad(5);
        contentTable.row();
        contentTable.add(new Label("Password:", skin)).left().pad(5);
        contentTable.row();
        contentTable.add(passwordField).width(200).pad(5);
        contentTable.row();
        contentTable.add(new Label("Confirm Password:", skin)).left().pad(5);
        contentTable.row();
        contentTable.add(confirmField).width(200).pad(5);

        TextButton createBtn = new TextButton("Create", skin);
        TextButton cancelBtn = new TextButton("Cancel", skin);

        createBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText();
                String conf = confirmField.getText();
                if (user.isEmpty() || pass.isEmpty()) {
                    uiService.getDialogFactory().showWarning("Invalid Input",
                        "Username and password cannot be empty.");
                    return;
                }
                if (!pass.equals(conf)) {
                    uiService.getDialogFactory().showWarning("Mismatch",
                        "Passwords do not match.");
                    return;
                }

                createDialog.hide();
                if (!multiplayerClient.isConnected()) {
                    multiplayerClient.setPendingCreateUserRequest(() -> multiplayerClient.createUser(user, pass));
                    connectToServer(config);
                } else {
                    multiplayerClient.createUser(user, pass);
                }
            }
        });

        cancelBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                createDialog.hide();
            }
        });

        createDialog.button(createBtn);
        createDialog.button(cancelBtn);

        createDialog.show(stage);
    }

    private void connectToServer(ServerConnectionConfig config) {
        connectingDialog = uiService.getDialogFactory().showLoading("Connecting to " + config.getServerName());
        multiplayerClient.setLoginResponseListener(this);
        multiplayerClient.setCreateUserResponseListener(this);

        worldService.setMultiplayerMode(true);
        multiplayerClient.connect(config.getServerIP(), config.getTcpPort(), config.getUdpPort());
    }

    @Override
    public void onLoginResponse(boolean success, String message, String username, int startX, int startY) {
        Gdx.app.postRunnable(() -> {
            if (connectingDialog != null) {
                connectingDialog.hide();
            }

            if (!success && message != null && message.contains("Disconnected")) {
                screenManager.showScreen(LoginScreen.class);
                return;
            }

            if (success) {
                PlayerData pd = playerService.getPlayerData();
                pd.setUsername(username);
                pd.setX(startX);
                pd.setY(startY);
                playerService.setPlayerData(pd);

                uiService.getDialogFactory().showSuccess("Connected", message);
                screenManager.disposeScreen(GameScreen.class);
                screenManager.showScreen(GameScreen.class);
            } else {
                uiService.getDialogFactory().showError("Connection Failed", message);
            }
        });
    }

    @Override
    public void onCreateUserResponse(boolean success, String message) {
        Gdx.app.postRunnable(() -> {
            if (connectingDialog != null) {
                connectingDialog.hide();
            }

            if (success) {
                uiService.getDialogFactory().showSuccess("Account Created", message);
            } else {
                uiService.getDialogFactory().showError("Registration Failed", message);
            }
        });
    }

    private void handleAddServer() {
        ServerConfigDialog dialog = new ServerConfigDialog(
            skin,
            uiService.getDialogFactory(),
            null,
            config -> {
                serverConnectionService.addServer(config);
                refreshServerList();
                uiService.getDialogFactory().showSuccess("Server Added", "Server successfully added!");
            }
        );
        dialog.show(stage);
    }

    private void handleEditServer() {
        ServerConnectionConfig selected = serverListView.getSelected();
        if (selected == null) {
            uiService.getDialogFactory().showWarning("No Server Selected", "Please select a server to edit.");
            return;
        }
        ServerConfigDialog dialog = new ServerConfigDialog(
            skin,
            uiService.getDialogFactory(),
            selected,
            config -> {
                serverConnectionService.saveConfig(config);
                refreshServerList();
                uiService.getDialogFactory().showSuccess("Server Updated", "Server successfully updated!");
            }
        );
        dialog.show(stage);
    }

    private void handleDeleteServer() {
        ServerConnectionConfig selected = serverListView.getSelected();
        if (selected == null) {
            uiService.getDialogFactory().showWarning("No Server Selected", "Please select a server to delete.");
            return;
        }

        uiService.getDialogFactory().showConfirmation(
            "Delete Server",
            "Are you sure you want to delete '" + selected.getServerName() + "'?",
            () -> {
                serverConnectionService.deleteServer(selected);
                refreshServerList();
            }
        );
    }

    private List<ServerConnectionConfig> loadServers() {
        return new ArrayList<>(serverConnectionService.listServers());
    }

    private void refreshServerList() {
        serverListView.setServers(loadServers());
    }

    private void setupInputHandling() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                
                if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    handleBack();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
        audioService.update(delta);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        if (root != null) {
            root.invalidateHierarchy();
            root.layout();
        }
    }

    @Override
    public void pause() {
        
    }

    @Override
    public void resume() {
        
    }

    @Override
    public void hide() {
        audioService.stopMenuMusic();
        connectionManager.releaseInstanceLock();
    }

    @Override
    public void dispose() {
        connectionManager.releaseInstanceLock();
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
    }
}
