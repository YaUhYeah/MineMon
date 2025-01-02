package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.audio.service.AudioService;
import io.github.minemon.core.service.ScreenManager;
import io.github.minemon.world.model.WorldData;
import io.github.minemon.world.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Component
@Slf4j
public class WorldSelectionScreen implements Screen {
    private final AudioService audioService;
    private final WorldService worldService;
    private final ScreenManager screenManager;

    @Value("${world.saveDir:save/worlds/}")
    private String saveDir;

    private Stage stage;
    private Skin skin;
    private Table worldListTable;
    private String selectedWorldName;

    private Table infoPanel;
    private TextButton playButton;
    private TextButton deleteButton;
    private float fontScale;

    @Autowired
    public WorldSelectionScreen(AudioService audioService,
                                WorldService worldService,
                                ScreenManager screenManager) {
        this.audioService = audioService;
        this.worldService = worldService;
        this.screenManager = screenManager;
    }

    @Override
    public void show() {
        audioService.playMenuMusic();

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("Skins/uiskin.json"));

        createUI();
        refreshWorldList();
    }

    private void createUI() {

        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        float maxUIWidth = 1400f;
        float maxUIHeight = 900f;



        fontScale = MathUtils.clamp(Math.min(width, height) / 900f, 0.8f, 1.5f);


        Table rootContainer = new Table(skin);
        rootContainer.setFillParent(true);
        stage.addActor(rootContainer);


        rootContainer.setBackground(skin.newDrawable("white", 0.1f,0.1f,0.1f,1f));


        Table mainTable = new Table(skin);
        mainTable.defaults().pad(10 * fontScale);
        rootContainer.add(mainTable)
                .width(Math.min(width * 0.9f, maxUIWidth))
                .height(Math.min(height * 0.9f, maxUIHeight))
                .center();


        Label titleLabel = new Label("Select World", skin);
        titleLabel.setFontScale(1.5f * fontScale);
        mainTable.add(titleLabel).colspan(2).padBottom(20 * fontScale).center().row();


        worldListTable = new Table(skin);
        worldListTable.top().defaults().pad(5 * fontScale);

        ScrollPane scrollPane = new ScrollPane(worldListTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);


        Table worldListContainer = new Table(skin);
        worldListContainer.setBackground(skin.newDrawable("white", 0.15f,0.15f,0.15f,1f));
        worldListContainer.add(scrollPane).expand().fill();

        infoPanel = new Table(skin);
        infoPanel.defaults().left().pad(5 * fontScale);
        infoPanel.setBackground(skin.newDrawable("white", 0.15f,0.15f,0.15f,1f));
        infoPanel.add(new Label("Select a world to view details", skin)).expand().fill();

        Table contentTable = new Table(skin);
        float contentWidth = Math.min((Math.min(width, maxUIWidth)) * 0.9f, 1200f);
        float infoPanelWidth = Math.min(contentWidth * 0.35f, 350f);
        float worldListWidth = Math.min(contentWidth * 0.6f, 600f);

        contentTable.add(worldListContainer).expandY().fillY().width(worldListWidth).padRight(20 * fontScale);
        contentTable.add(infoPanel).expandY().fillY().width(infoPanelWidth);

        mainTable.add(contentTable).colspan(2).expand().fill().row();


        TextButton createButton = new TextButton("Create New World", skin);
        playButton = new TextButton("Play Selected World", skin);
        deleteButton = new TextButton("Delete World", skin);
        TextButton backButton = new TextButton("Back", skin);


        createButton.getLabel().setFontScale(fontScale);
        playButton.getLabel().setFontScale(fontScale);
        deleteButton.getLabel().setFontScale(fontScale);
        backButton.getLabel().setFontScale(fontScale);


        playButton.setDisabled(true);
        deleteButton.setDisabled(true);


        createButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showCreateWorldDialog();
            }
        });

        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedWorldName != null) {
                    log.debug("PLAY requested for world '{}'", selectedWorldName);
                    worldService.loadWorld(selectedWorldName);
                    screenManager.showScreen(GameScreen.class);
                }
            }
        });


        deleteButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedWorldName != null) {
                    showDeleteConfirmDialog();
                }
            }
        });

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                screenManager.goBack();
            }
        });

        Table buttonTable = new Table(skin);
        buttonTable.defaults().pad(10 * fontScale);
        buttonTable.add(createButton);
        buttonTable.add(playButton);
        buttonTable.add(deleteButton);
        buttonTable.row();
        buttonTable.add(backButton).colspan(3).center().padTop(10 * fontScale);

        mainTable.row();
        mainTable.add(buttonTable).colspan(2).padTop(20 * fontScale).center();
    }

    private void showDeleteConfirmDialog() {
        if (selectedWorldName == null) return;
        Dialog dialog = new Dialog("Delete World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {

                    worldService.deleteWorld(selectedWorldName);
                    refreshWorldList();
                    selectedWorldName = null;
                    updateInfoPanel(null);
                    updateButtonsState();
                }
            }
        };
        dialog.text("Are you sure you want to delete world '" + selectedWorldName + "'?\nThis action cannot be undone!");
        dialog.button("Delete", true);
        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private Table createWorldEntry(final String worldName) {
        Table entry = new Table(skin);
        entry.setBackground(skin.newDrawable("white", 0.1f,0.1f,0.1f,1f));

        WorldData meta = loadWorldMetadata(worldName);

        FileHandle iconFile = Gdx.files.local(saveDir + worldName + "/icon.png");
        Image icon;
        if (iconFile.exists()) {
            Texture tex = new Texture(iconFile);
            icon = new Image(new TextureRegion(tex));
        } else {

            FileHandle fallbackFile = Gdx.files.internal("icons/default_world_icon.png");
            Texture fallback = new Texture(fallbackFile);
            icon = new Image(new TextureRegion(fallback));
        }

        icon.setScaling(Scaling.fit);
        float iconSize = 64f * fontScale;
        entry.add(icon).size(iconSize, iconSize).pad(10 * fontScale);

        Table infoTable = new Table(skin);
        Label nameLabel = new Label(worldName, skin);
        nameLabel.setFontScale(fontScale);

        Label createdLabel = new Label("Created: " + formatDate(meta.getCreatedDate()), skin);
        createdLabel.setFontScale(fontScale);

        Label lastPlayedLabel = new Label("Last Played: " + formatDate(meta.getLastPlayed()), skin);
        lastPlayedLabel.setFontScale(fontScale);

        Label playedTimeLabel = new Label("Played Time: " + formatPlayedTime(meta.getPlayedTime()), skin);
        playedTimeLabel.setFontScale(fontScale);

        infoTable.add(nameLabel).left().row();
        infoTable.add(createdLabel).left().row();
        infoTable.add(lastPlayedLabel).left().row();
        infoTable.add(playedTimeLabel).left().row();

        entry.add(infoTable).expand().fill().pad(10 * fontScale);

        entry.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedWorldName = worldName;
                highlightSelectedEntry(entry);
                updateInfoPanel(meta);
                updateButtonsState();
            }
        });
        return entry;
    }

    private void highlightSelectedEntry(Table selectedEntry) {
        for (Actor actor : worldListTable.getChildren()) {
            if (actor instanceof Table t) {
                t.setBackground(skin.newDrawable("white", 0.1f,0.1f,0.1f,1f));
            }
        }
        selectedEntry.setBackground(skin.newDrawable("white", 0.3f,0.6f,1f,1f));
    }

    private WorldData loadWorldMetadata(String worldName) {
        FileHandle worldFile = Gdx.files.local(saveDir + worldName + "/" + worldName + ".json");
        if (!worldFile.exists()) {
            WorldData wd = new WorldData();
            wd.setWorldName(worldName);
            wd.setSeed(0);
            wd.setCreatedDate(0);
            wd.setLastPlayed(0);
            wd.setPlayedTime(0);
            return wd;
        }
        Json json = new Json();
        return json.fromJson(WorldData.class, worldFile.readString());
    }

    private String formatPlayedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds %= 60;
        minutes %= 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    private String formatDate(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("MMM d, yyyy HH:mm").format(new Date(timestamp));
    }

    private void refreshWorldList() {
        worldListTable.clear();
        List<String> worldNames = worldService.getAvailableWorlds();
        for (String worldName : worldNames) {
            Table entry = createWorldEntry(worldName);
            worldListTable.add(entry).expandX().fillX().row();
        }
        selectedWorldName = null;
        updateButtonsState();
        updateInfoPanel(null);
    }

    private void updateButtonsState() {
        boolean hasSelection = selectedWorldName != null;
        playButton.setDisabled(!hasSelection);
        deleteButton.setDisabled(!hasSelection);
    }private void showCreateWorldDialog() {
        Dialog dialog = new Dialog("Create New World", skin) {
            @Override
            protected void result(Object object) {
                if ((Boolean) object) {
                    TextField nameField = findActor("worldNameField");
                    TextField seedField = findActor("seedField");

                    String worldName = nameField.getText().trim();
                    if (worldName.isEmpty()) {
                        showErrorDialog("World name cannot be empty");
                        return;
                    }

                    long seed;
                    String seedText = seedField.getText().trim();
                    if (seedText.isEmpty()) {
                        seed = new Random().nextLong();
                    } else {
                        try {
                            seed = Long.parseLong(seedText);
                        } catch (NumberFormatException e) {
                            showErrorDialog("Seed must be a valid number");
                            return;
                        }
                    }

                    boolean success = worldService.createWorld(worldName, seed);
                    if (!success) {
                        showErrorDialog("Failed to create the world. The name might already exist or another error occurred.");
                        return;
                    }


                    worldService.generateWorldThumbnail(worldName);

                    refreshWorldList();
                    selectedWorldName = worldName;
                    highlightNewlyCreatedEntry(worldName);
                    updateButtonsState();
                    updateInfoPanel(loadWorldMetadata(worldName));
                }
            }
        };

        dialog.getContentTable().pad(10 * fontScale);

        Label worldNameLabel = new Label("World Name:", skin);
        worldNameLabel.setFontScale(fontScale);
        dialog.getContentTable().add(worldNameLabel).left().row();

        TextField nameField = new TextField("", skin);
        nameField.setName("worldNameField");
        nameField.setMessageText("Enter world name");
        dialog.getContentTable().add(nameField).width(300 * fontScale).padBottom(10 * fontScale).row();

        Label seedLabel = new Label("Seed (optional):", skin);
        seedLabel.setFontScale(fontScale);
        dialog.getContentTable().add(seedLabel).left().row();

        TextField seedField = new TextField("", skin);
        seedField.setName("seedField");
        seedField.setMessageText("Enter seed or leave empty");
        dialog.getContentTable().add(seedField).width(300 * fontScale).padBottom(10 * fontScale).row();

        dialog.button("Create", true);
        dialog.button("Cancel", false);
        dialog.key(Input.Keys.ENTER, true);
        dialog.key(Input.Keys.ESCAPE, false);

        dialog.show(stage);
    }


    private void highlightNewlyCreatedEntry(String worldName) {
        for (Actor actor : worldListTable.getChildren()) {
            if (actor instanceof Table entry) {
                Label nameLabel = findNameLabel(entry);
                if (nameLabel != null && worldName.equals(nameLabel.getText().toString())) {
                    highlightSelectedEntry(entry);
                    break;
                }
            }
        }
    }

    private Label findNameLabel(Table entry) {
        for (Actor child : entry.getChildren()) {
            if (child instanceof Table infoTable) {
                for (Actor infoChild : infoTable.getChildren()) {
                    if (infoChild instanceof Label label) {
                        return label;
                    }
                }
            }
        }
        return null;
    }

    private void updateInfoPanel(WorldData meta) {
        infoPanel.clear();
        if (meta == null) {
            Label prompt = new Label("Select a world to view details", skin);
            prompt.setFontScale(fontScale);
            infoPanel.add(prompt).expand().fill();
            return;
        }

        infoPanel.defaults().left().pad(5 * fontScale);
        Label nameLabel = new Label(meta.getWorldName(), skin);
        nameLabel.setFontScale(1.2f * fontScale);
        infoPanel.add(nameLabel).expandX().row();

        Label created = new Label("Created: " + formatDate(meta.getCreatedDate()), skin);
        created.setFontScale(fontScale);
        infoPanel.add(created).row();

        Label lastPlayed = new Label("Last Played: " + formatDate(meta.getLastPlayed()), skin);
        lastPlayed.setFontScale(fontScale);
        infoPanel.add(lastPlayed).row();

        Label playedTime = new Label("Played Time: " + formatPlayedTime(meta.getPlayedTime()), skin);
        playedTime.setFontScale(fontScale);
        infoPanel.add(playedTime).row();
    }

    private void showErrorDialog(String message) {
        Dialog errorDialog = new Dialog("Error", skin);
        errorDialog.text(message);
        errorDialog.button("OK");
        errorDialog.show(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            screenManager.goBack();
        }

        Gdx.gl.glClearColor(0.15f,0.15f,0.15f,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
        audioService.update(delta);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        audioService.stopMenuMusic();
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
