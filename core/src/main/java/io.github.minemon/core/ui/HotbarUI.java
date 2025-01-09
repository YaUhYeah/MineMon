package io.github.minemon.core.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
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
    private static final int HOTBAR_START_INDEX = 27;

    private final Map<Integer, Integer> hotbarKeys = new HashMap<>();
    private final InventoryService inventoryService;
    private final ItemTextureManager textureManager;
    private final UiService uiService;
    private final Color SELECTED_COLOR = new Color(1, 1, 1, 0.8f);
    private final Color UNSELECTED_COLOR = new Color(0.5f, 0.5f, 0.5f, 0.6f);
    @Getter
    private Stage stage;
    private Skin skin;
    private Table mainTable;
    private Table[] slots;
    @Getter
    private int selectedSlot = 0;
    private boolean initialized = false;
    private Window tooltipWindow;
    
    private TextureAtlas atlas;

    public HotbarUI(UiService uiService,
                    InventoryService inventoryService,
                    ItemTextureManager textureManager) {
        this.uiService = uiService;
        this.inventoryService = inventoryService;
        this.textureManager = textureManager;
        this.slots = new Table[HOTBAR_SIZE];
        initializeDefaultKeybindings();
    }

    public void initialize() {
        if (initialized) return;

        
        this.skin = uiService.getSkin();

        
        this.atlas = new TextureAtlas(Gdx.files.internal("atlas/ui-gfx-atlas.atlas"));
        
        this.skin.addRegions(atlas);

        this.stage = new Stage(new ScreenViewport());

        
        mainTable = new Table(skin);
        mainTable.setFillParent(true);
        mainTable.align(Align.bottom);
        mainTable.padBottom(20);

        setupHotbar();
        setupInput();

        stage.addActor(mainTable);
        initialized = true;
    }

    public void dispose() {
        if (tooltipWindow != null) {
            tooltipWindow.remove();
        }
        if (stage != null) {
            stage.dispose();
        }
        if (atlas != null) {
            atlas.dispose();
        }
        initialized = false;
    }

    private void setupHotbar() {
        Table hotbarTable = new Table(skin);
        
        hotbarTable.setBackground(tintedDrawable("hotbar_bg", new Color(0, 0, 0, 0.7f)));
        hotbarTable.pad(4);

        
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            Label numberLabel = new Label(String.valueOf(i + 1), skin);
            numberLabel.setAlignment(Align.center);
            hotbarTable.add(numberLabel).padBottom(2);
            if (i < HOTBAR_SIZE - 1) {
                hotbarTable.add().width(4); 
            }
        }
        hotbarTable.row();

        
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            Table slot = createSlot(i);
            slots[i] = slot;
            hotbarTable.add(slot).size(SLOT_SIZE);

            if (i < HOTBAR_SIZE - 1) {
                hotbarTable.add().width(4); 
            }
        }

        mainTable.add(hotbarTable);

        
        updateSelection();
    }

    private Table createSlot(final int index) {
        Table slot = new Table(skin);
        slot.setBackground(tintedDrawable("hotbar_bg", UNSELECTED_COLOR));

        
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

    private void showTooltip(InventorySlot slot, float x, float y) {
        if (tooltipWindow != null) {
            tooltipWindow.remove();
        }

        tooltipWindow = new Window("", skin);
        tooltipWindow.setMovable(false);
        tooltipWindow.setTouchable(Touchable.disabled);

        Table content = new Table(skin);
        content.pad(8);

        
        InventoryItem item = inventoryService.getItemRegistry().get(slot.getItemId());
        if (item != null) {
            
            Label nameLabel = new Label(item.getName(), skin);
            nameLabel.setWrap(true);
            content.add(nameLabel).width(200).row();

            
            if (slot.getMaxDurability() > 0) {
                Label durabilityLabel = new Label(
                    String.format("Durability: %d/%d", slot.getDurability(), slot.getMaxDurability()),
                    skin
                );
                durabilityLabel.getStyle().fontColor = Color.LIGHT_GRAY;
                content.add(durabilityLabel).padTop(4).row();
            }

            
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
        
        stage.addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                int newSlot = selectedSlot + (amountY > 0 ? 1 : -1);
                
                if (newSlot < 0) newSlot = HOTBAR_SIZE - 1;
                if (newSlot >= HOTBAR_SIZE) newSlot = 0;

                setSelectedSlot(newSlot);
                return true;
            }
        });
    }

    private void initializeDefaultKeybindings() {
        
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

        
        log.debug("Selected hotbar slot: {}", slot);
    }

    private void updateSelection() {
        for (int i = 0; i < slots.length; i++) {
            String regionName = (i == selectedSlot)
                ? "slot_selected"
                : "slot_normal";
            Color color = (i == selectedSlot)
                ? SELECTED_COLOR
                : UNSELECTED_COLOR;
            slots[i].setBackground(tintedDrawable(regionName, color));
        }
    }


    public void update() {
        if (!initialized) {
            initialize();
        }
        
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
                
                Image itemImage = new Image(texture);
                itemImage.setScaling(Scaling.fit);
                slotTable.add(itemImage).size(SLOT_SIZE - 8).pad(4);

                
                if (slot.getCount() > 1) {
                    Label.LabelStyle countStyle = new Label.LabelStyle(skin.get(Label.LabelStyle.class));
                    
                    countStyle.background = tintedDrawable("hotbar_bg", new Color(0, 0, 0, 0.5f));
                    Label countLabel = new Label(String.valueOf(slot.getCount()), countStyle);
                    countLabel.setAlignment(Align.center);
                    slotTable.add(countLabel).size(20, 20).expand().right().bottom().pad(2);
                }

                
                if (slot.getMaxDurability() > 0) {
                    addDurabilityBar(slotTable, slot);
                }
            }
        }
    }

    private void addDurabilityBar(Table slotTable, InventorySlot slot) {
        Table durabilityBar = new Table();
        
        durabilityBar.setBackground(tintedDrawable("hotbar_bg", new Color(0.3f, 0.3f, 0.3f, 0.7f)));

        float durabilityPercent = slot.getDurability() / (float) slot.getMaxDurability();
        Color fillColor = getDurabilityColor(durabilityPercent);

        Table durabilityFill = new Table();
        durabilityFill.setBackground(tintedDrawable("hotbar_bg", fillColor));

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
            return new Color(0.2f, 0.8f, 0.2f, 0.8f); 
        } else if (percent > 0.25f) {
            return new Color(0.8f, 0.8f, 0.2f, 0.8f); 
        } else {
            return new Color(0.8f, 0.2f, 0.2f, 0.8f); 
        }
    }

    public void render() {
        if (!initialized) {
            initialize();
        }
        handleInput();
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    private Drawable tintedDrawable(String regionName, Color tint) {
        TextureRegion region = atlas.findRegion(regionName);
        if (region == null) {
            return new TextureRegionDrawable();
        }

        
        Sprite sprite = new Sprite(region);
        sprite.setColor(tint);

        
        SpriteDrawable spriteDrawable = new SpriteDrawable(sprite);
        return spriteDrawable;
    }

}
