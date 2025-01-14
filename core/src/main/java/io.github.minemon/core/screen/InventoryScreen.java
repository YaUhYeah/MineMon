package io.github.minemon.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.minemon.core.service.UiService;
import io.github.minemon.input.InputConfiguration;
import io.github.minemon.input.InputService;
import io.github.minemon.inventory.model.InventoryItem;
import io.github.minemon.inventory.model.InventorySlot;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.inventory.service.impl.ItemTextureManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InventoryScreen {
    private static final int SLOT_SIZE = 40;
    private static final int INVENTORY_COLS = 9;
    private static final int INVENTORY_ROWS = 3;
    private static final int CRAFTING_SIZE = 2;

    private final InventoryService inventoryService;
    private final UiService uiService;
    private final InputService inputService;

    private Stage stage;
    @Getter
    private boolean visible;
    private Table mainTable;
    private Table craftingTable;
    private Table inventoryTable;
    private Table hotbarTable;
    private Group dragGroup;
    private Image draggedImage;
    private Label draggedCountLabel;
    private InventorySlot draggedSlot;
    private int dragSourceIndex = -1;
    private Vector2 dragOffset = new Vector2();
    private InputProcessor previousProcessor;

    @Autowired
    private InputConfiguration inputConfig;

    @Autowired
    private ItemTextureManager textureManager;

    
    private TextureAtlas uiAtlas;

    @Autowired
    public InventoryScreen(InventoryService inventoryService, UiService uiService, InputService inputService) {
        this.inventoryService = inventoryService;
        this.uiService = uiService;
        this.inputService = inputService;
    }

    public void init() {
        if (stage != null) return;
        stage = new Stage(new ScreenViewport());

        
        uiAtlas = new TextureAtlas(Gdx.files.internal("atlas/ui-gfx-atlas.atlas"));

        setupUI();
        setupDragAndDrop();
    }

    public void setupUI() {
        Skin skin = uiService.getSkin();

        mainTable = new Table(skin);
        mainTable.setFillParent(true);
        mainTable.center();

        
        mainTable.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("hotbar_bg"))
                .tint(new Color(0f, 0f, 0f, 0.85f))
        );

        
        craftingTable = new Table(skin);
        craftingTable.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("hotbar_bg"))
                .tint(new Color(0.2f, 0.2f, 0.2f, 0.8f))
        );

        
        for (int i = 0; i < CRAFTING_SIZE; i++) {
            for (int j = 0; j < CRAFTING_SIZE; j++) {
                Table slot = createCraftingSlot(i * CRAFTING_SIZE + j);
                craftingTable.add(slot).size(SLOT_SIZE).pad(2);
            }
            craftingTable.row();
        }

        
        Image arrowImage = new Image(
            new TextureRegionDrawable(uiAtlas.findRegion("arrow"))
        );
        craftingTable.add(arrowImage).size(32, 32).padLeft(10).padRight(10);

        
        Table resultSlot = createCraftingResultSlot();
        craftingTable.add(resultSlot).size(SLOT_SIZE).pad(2);

        mainTable.add(craftingTable).padBottom(20).row();

        
        inventoryTable = new Table(skin);
        inventoryTable.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("hotbar_bg"))
                .tint(new Color(0.2f, 0.2f, 0.2f, 0.8f))
        );

        for (int i = 0; i < INVENTORY_ROWS; i++) {
            for (int j = 0; j < INVENTORY_COLS; j++) {
                final int index = i * INVENTORY_COLS + j;
                Table slot = createInventorySlot(index);
                inventoryTable.add(slot).size(SLOT_SIZE).pad(2);
            }
            inventoryTable.row();
        }

        mainTable.add(inventoryTable).pad(20).row();

        
        hotbarTable = new Table(skin);
        hotbarTable.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("hotbar_bg"))
                .tint(new Color(0.25f, 0.25f, 0.25f, 0.9f))
        );

        for (int i = 0; i < 9; i++) {
            final int index = 27 + i; 
            Table slot = createInventorySlot(index);
            hotbarTable.add(slot).size(SLOT_SIZE).pad(2);
        }

        mainTable.add(hotbarTable).padTop(10).row();

        
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });
        mainTable.add(closeButton).size(100, 40).pad(10);

        stage.addActor(mainTable);
        mainTable.setVisible(false);
    }

    private Table createCraftingSlot(final int index) {
        Table slot = new Table(uiService.getSkin());
        
        slot.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("slot_normal"))
                .tint(new Color(0.2f, 0.2f, 0.2f, 0.8f))
        );
        return slot;
    }

    private Table createInventorySlot(final int index) {
        Table slot = new Table(uiService.getSkin());
        
        slot.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("slot_normal"))
                .tint(new Color(0.2f, 0.2f, 0.2f, 0.8f))
        );

        
        slot.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;

                InventorySlot inventorySlot = inventoryService.getInventory().get(index);
                if (inventorySlot.getItemId() != null) {
                    startDragging(inventorySlot, index, x, y);
                    return true;
                }
                return false;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (draggedSlot != null) {
                    finishDragging(index);
                }
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (draggedSlot != null) {
                    updateDragPosition(event.getStageX(), event.getStageY());
                }
            }
        });

        return slot;
    }

    private Table createCraftingResultSlot() {
        Table slot = new Table(uiService.getSkin());
        slot.setBackground(
            new TextureRegionDrawable(uiAtlas.findRegion("slot_normal"))
                .tint(new Color(0.3f, 0.3f, 0.3f, 0.8f))
        );
        return slot;
    }

    private void setupDragAndDrop() {
        dragGroup = new Group();
        stage.addActor(dragGroup);

        draggedImage = new Image();
        draggedImage.setSize(SLOT_SIZE, SLOT_SIZE);

        Label.LabelStyle countStyle = new Label.LabelStyle(uiService.getSkin().get(Label.LabelStyle.class));
        
        countStyle.background = uiService.getSkin().newDrawable("white", new Color(0, 0, 0, 0.5f));
        draggedCountLabel = new Label("", countStyle);
        draggedCountLabel.setSize(20, 20);
        draggedCountLabel.setAlignment(Align.center);

        dragGroup.addActor(draggedImage);
        dragGroup.addActor(draggedCountLabel);
        dragGroup.setVisible(false);
        dragGroup.setZIndex(9999);
    }

    private void updateSlotDisplay(Table slotTable, InventorySlot slot) {
        slotTable.clearChildren();

        if (slot.getItemId() != null && slot.getCount() > 0) {
            TextureRegion texture = textureManager.getTexture(slot.getItemId());
            if (texture != null) {
                
                Table background = new Table();
                background.setBackground(uiService.getSkin().newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.8f)));
                slotTable.add(background).grow();

                
                Image itemImage = new Image(texture);
                itemImage.setScaling(Scaling.fit);
                slotTable.add(itemImage).size(SLOT_SIZE - 8).pad(4).expand().center();

                
                if (slot.getCount() > 1) {
                    Label.LabelStyle countStyle = new Label.LabelStyle(uiService.getSkin().get(Label.LabelStyle.class));
                    countStyle.background = uiService.getSkin().newDrawable("white", new Color(0, 0, 0, 0.5f));
                    Label countLabel = new Label(String.valueOf(slot.getCount()), countStyle);
                    countLabel.setAlignment(Align.center);
                    slotTable.add(countLabel).size(20, 20).expand().right().bottom().pad(2);
                }

                
                if (slot.getMaxDurability() > 0) {
                    addDurabilityBar(slotTable, slot);
                }

                
                addTooltip(itemImage, slot);
            }
        }

        
        addHoverEffect(slotTable);
    }

    private void addDurabilityBar(Table slotTable, InventorySlot slot) {
        Table durabilityBar = new Table();
        durabilityBar.setBackground(uiService.getSkin().newDrawable("white", new Color(0.3f, 0.3f, 0.3f, 0.7f)));
        float durabilityPercent = slot.getDurability() / (float) slot.getMaxDurability();

        Color fillColor = getDurabilityColor(durabilityPercent);
        Table durabilityFill = new Table();
        durabilityFill.setBackground(uiService.getSkin().newDrawable("white", fillColor));

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

    private void addTooltip(Image itemImage, InventorySlot slot) {
        InventoryItem itemDef = inventoryService.getItemRegistry().get(slot.getItemId());
        if (itemDef != null) {
            Table tooltipContent = new Table(uiService.getSkin());
            tooltipContent.setBackground(uiService.getSkin().newDrawable("white", new Color(0, 0, 0, 0.8f)));
            tooltipContent.pad(4);

            Label nameLabel = new Label(itemDef.getName(), uiService.getSkin());
            nameLabel.setColor(Color.WHITE);
            tooltipContent.add(nameLabel).row();

            if (slot.getMaxDurability() > 0) {
                Label durabilityLabel = new Label(
                    String.format("Durability: %d/%d", slot.getDurability(), slot.getMaxDurability()),
                    uiService.getSkin()
                );
                durabilityLabel.setColor(Color.LIGHT_GRAY);
                tooltipContent.add(durabilityLabel);
            }

            Tooltip<Table> tooltip = new Tooltip<>(tooltipContent);
            tooltip.setInstant(true);
            TooltipManager.getInstance().instant();
            itemImage.addListener(tooltip);
        }
    }

    private void addHoverEffect(Table slotTable) {
        final Drawable normalBackground =
            new TextureRegionDrawable(uiAtlas.findRegion("slot_normal"))
                .tint(new Color(0.2f, 0.2f, 0.2f, 0.8f));
        final Drawable hoverBackground =
            new TextureRegionDrawable(uiAtlas.findRegion("slot_selected"));

        slotTable.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (!dragGroup.isVisible() && hoverBackground != null) {
                    slotTable.setBackground(hoverBackground);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (!dragGroup.isVisible()) {
                    slotTable.setBackground(normalBackground);
                }
            }
        });
    }

    public void show() {
        if (!visible) {
            visible = true;
            mainTable.setVisible(true);
            previousProcessor = Gdx.input.getInputProcessor();
            update();

            stage.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == inputConfig.getActionKey("INVENTORY") ||
                        keycode == Input.Keys.ESCAPE) {
                        hide();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    public void hide() {
        if (visible) {
            visible = false;
            if (mainTable != null) {
                mainTable.setVisible(false);
            }
            if (dragGroup != null) {
                dragGroup.setVisible(false);
            }
            draggedSlot = null;
            dragSourceIndex = -1;
            if (previousProcessor != null) {
                Gdx.input.setInputProcessor(previousProcessor);
                previousProcessor = null;
            }
        }
    }

    private void startDragging(InventorySlot slot, int sourceIndex, float x, float y) {
        draggedSlot = slot.copy();
        dragSourceIndex = sourceIndex;

        TextureRegion texture = textureManager.getTexture(slot.getItemId());
        if (texture != null) {
            draggedImage.setDrawable(new TextureRegionDrawable(texture));
            if (slot.getCount() > 1) {
                draggedCountLabel.setText(String.valueOf(slot.getCount()));
                draggedCountLabel.setVisible(true);
            } else {
                draggedCountLabel.setVisible(false);
            }

            dragGroup.setVisible(true);
            dragOffset.set(x, y);
            updateDragPosition(x, y);
        }
    }

    private void updateDragPosition(float x, float y) {
        dragGroup.setPosition(
            x - dragOffset.x,
            y - dragOffset.y
        );
    }

    private void finishDragging(int targetIndex) {
        if (dragSourceIndex != targetIndex) {
            
            update();
        }

        draggedSlot = null;
        dragSourceIndex = -1;
        dragGroup.setVisible(false);
    }

    public void update() {
        if (!visible) return;

        
        
        
        
    }

    public void toggleVisibility() {
        if (visible) {
            hide();
        } else {
            show();
        }
    }

    public void render(float delta) {
        if (!visible) return;
        stage.act(delta);
        stage.draw();
    }

    public void dispose() {
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        
        if (uiAtlas != null) {
            uiAtlas.dispose();
            uiAtlas = null;
        }
    }
}
