package io.github.minemon.core.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.core.service.UiService;
import io.github.minemon.inventory.model.InventoryItem;
import io.github.minemon.inventory.model.InventorySlot;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.inventory.service.impl.ItemTextureManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class HotbarUI {
    private static final int SLOT_SIZE = 40;
    private static final int HOTBAR_SIZE = 9;
    private static final int HOTBAR_START_INDEX = 27; // Assuming hotbar is after main inventory

    @Getter
    private final Stage stage;
    private final Skin skin;
    private final Table mainTable;
    private final Table[] slots;
    @Getter
    private int selectedSlot = 0;

    private final InventoryService inventoryService;
    private final ItemTextureManager textureManager;

    private final Color SELECTED_COLOR = new Color(1, 1, 1, 0.8f);
    private final Color UNSELECTED_COLOR = new Color(0.5f, 0.5f, 0.5f, 0.6f);

    public HotbarUI(UiService uiService,
                    InventoryService inventoryService,
                    ItemTextureManager textureManager) {
        this.inventoryService = inventoryService;
        this.textureManager = textureManager;
        this.skin = uiService.getSkin();
        this.stage = new Stage(new ScreenViewport());
        this.slots = new Table[HOTBAR_SIZE];

        // Create main table
        mainTable = new Table(skin);
        mainTable.setFillParent(true);
        mainTable.align(Align.bottom);
        mainTable.padBottom(20); // Space from bottom of screen

        initializeDefaultKeybindings();
        setupHotbar();
        setupInput();

        stage.addActor(mainTable);
    }

    public void dispose() {
        if (tooltipWindow != null) {
            tooltipWindow.remove();
        }
        stage.dispose();
    }

    private void setupHotbar() {
        Table hotbarTable = new Table(skin);
        hotbarTable.setBackground(skin.newDrawable("hotbar-bg", new Color(0, 0, 0, 0.7f)));
        hotbarTable.pad(4);

        // Create slots
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            final int index = i;
            Table slot = createSlot(i);
            slots[i] = slot;

            // Add number label above slot
            Label numberLabel = new Label(String.valueOf(i + 1), skin);
            numberLabel.setAlignment(Align.center);
            hotbarTable.add(numberLabel).padBottom(2);

            if (i < HOTBAR_SIZE - 1) {
                hotbarTable.add().width(4); // Space between numbers
            }
        }
        hotbarTable.row();

        // Add slots
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            hotbarTable.add(slots[i]).size(SLOT_SIZE);

            if (i < HOTBAR_SIZE - 1) {
                hotbarTable.add().width(4); // Space between slots
            }
        }

        mainTable.add(hotbarTable);

        // Highlight initial selection
        updateSelection();
    }

    private Table createSlot(final int index) {
        Table slot = new Table(skin);
        slot.setBackground(skin.newDrawable("hotbar-bg", UNSELECTED_COLOR));

        // Add click listener
        slot.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setSelectedSlot(index);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                InventorySlot inventorySlot = inventoryService.getInventory().get(HOTBAR_START_INDEX + index);
                if (inventorySlot.getItemId() != null) {
                    showTooltip(inventorySlot, event.getStageX(), event.getStageY());
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                hideTooltip();
            }
        });

        return slot;
    }

    private Window tooltipWindow;

    private void showTooltip(InventorySlot slot, float x, float y) {
        if (tooltipWindow != null) {
            tooltipWindow.remove();
        }

        tooltipWindow = new Window("", skin);
        tooltipWindow.setMovable(false);
        tooltipWindow.setTouchable(Touchable.disabled);

        Table content = new Table(skin);
        content.pad(8);

        // Get item definition
        InventoryItem item = inventoryService.getItemRegistry().get(slot.getItemId());
        if (item != null) {
            // Item name
            Label nameLabel = new Label(item.getName(), skin);
            nameLabel.setWrap(true);
            content.add(nameLabel).width(200).row();

            // Add durability if applicable
            if (slot.getMaxDurability() > 0) {
                Label durabilityLabel = new Label(
                    String.format("Durability: %d/%d", slot.getDurability(), slot.getMaxDurability()),
                    skin
                );
                durabilityLabel.getStyle().fontColor = Color.LIGHT_GRAY;
                content.add(durabilityLabel).padTop(4).row();
            }

            // Add stack size if applicable
            if (item.getMaxStackSize() > 1) {
                Label stackLabel = new Label(
                    String.format("Stack: %d/%d", slot.getCount(), item.getMaxStackSize()),
                    skin
                );
                stackLabel.getStyle().fontColor = Color.LIGHT_GRAY;
                content.add(stackLabel).padTop(4).row();
            }
        }

        tooltipWindow.add(content);
        tooltipWindow.pack();

        // Position tooltip above slot
        float tooltipX = Math.min(x, stage.getWidth() - tooltipWindow.getWidth());
        float tooltipY = Math.min(y + 40, stage.getHeight() - tooltipWindow.getHeight());
        tooltipWindow.setPosition(tooltipX, tooltipY);

        stage.addActor(tooltipWindow);
    }

    private void hideTooltip() {
        if (tooltipWindow != null) {
            tooltipWindow.remove();
            tooltipWindow = null;
        }
    }

    private void setupInput() {
        // Mouse wheel scrolling
        stage.addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                int newSlot = selectedSlot + (amountY > 0 ? 1 : -1);

                // Wrap around
                if (newSlot < 0) newSlot = HOTBAR_SIZE - 1;
                if (newSlot >= HOTBAR_SIZE) newSlot = 0;

                setSelectedSlot(newSlot);
                return true;
            }
        });
    }

    private final Map<Integer, Integer> hotbarKeys = new HashMap<>();

    private void initializeDefaultKeybindings() {
        // Default to number keys 1-9
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            hotbarKeys.put(i, Input.Keys.NUM_1 + i);
        }
    }

    public void setHotbarKeybinding(int slotIndex, int keycode) {
        if (slotIndex >= 0 && slotIndex < HOTBAR_SIZE) {
            hotbarKeys.put(slotIndex, keycode);
            log.debug("Set hotbar slot {} keybinding to {}", slotIndex, Input.Keys.toString(keycode));
        }
    }

    public void handleInput() {
        // Check configured keybindings
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            Integer keycode = hotbarKeys.get(i);
            if (keycode != null && Gdx.input.isKeyJustPressed(keycode)) {
                setSelectedSlot(i);
                break;
            }
        }
    }

    public void setSelectedSlot(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return;

        selectedSlot = slot;
        updateSelection();

        // Fire event or callback if needed
        log.debug("Selected hotbar slot: {}", slot);
    }

    private void updateSelection() {
        for (int i = 0; i < slots.length; i++) {
            slots[i].setBackground(skin.newDrawable("hotbar-bg",
                i == selectedSlot ? SELECTED_COLOR : UNSELECTED_COLOR));
        }
    }

    public void update() {
        // Update slot contents from inventory
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            InventorySlot inventorySlot = inventoryService.getInventory().get(HOTBAR_START_INDEX + i);
            updateSlotDisplay(slots[i], inventorySlot);
        }
    }

    private void updateSlotDisplay(Table slotTable, InventorySlot slot) {
        slotTable.clear();

        if (slot.getItemId() != null && slot.getCount() > 0) {
            TextureRegion texture = textureManager.getTexture(slot.getItemId());
            if (texture != null) {
                // Item image
                Image itemImage = new Image(texture);
                itemImage.setScaling(Scaling.fit);
                slotTable.add(itemImage).size(SLOT_SIZE - 8).pad(4);

                // Stack count if > 1
                if (slot.getCount() > 1) {
                    Label.LabelStyle countStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
                    countStyle.background = skin.newDrawable("hotbar-bg", new Color(0, 0, 0, 0.5f));
                    Label countLabel = new Label(String.valueOf(slot.getCount()), countStyle);
                    countLabel.setAlignment(Align.center);
                    slotTable.add(countLabel).size(20, 20).expand().right().bottom().pad(2);
                }

                // Add durability bar if needed
                if (slot.getMaxDurability() > 0) {
                    addDurabilityBar(slotTable, slot);
                }
            }
        }
    }

    private void addDurabilityBar(Table slotTable, InventorySlot slot) {
        Table durabilityBar = new Table();
        durabilityBar.setBackground(skin.newDrawable("hotbar-bg", new Color(0.3f, 0.3f, 0.3f, 0.7f)));

        float durabilityPercent = slot.getDurability() / (float) slot.getMaxDurability();
        Color fillColor = getDurabilityColor(durabilityPercent);

        Table durabilityFill = new Table();
        durabilityFill.setBackground(skin.newDrawable("hotbar-bg", fillColor));

        durabilityBar.add(durabilityFill)
            .width((SLOT_SIZE - 8) * durabilityPercent)
            .height(2)
            .grow()
            .left();

        slotTable.add(durabilityBar)
            .width(SLOT_SIZE - 8)
            .height(2)
            .expand()
            .bottom()
            .padBottom(2);
    }

    private Color getDurabilityColor(float percent) {
        if (percent > 0.5f) {
            return new Color(0.2f, 0.8f, 0.2f, 0.8f); // Green
        } else if (percent > 0.25f) {
            return new Color(0.8f, 0.8f, 0.2f, 0.8f); // Yellow
        } else {
            return new Color(0.8f, 0.2f, 0.2f, 0.8f); // Red
        }
    }

    public void render() {
        handleInput();
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

}
