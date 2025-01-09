package io.github.minemon.inventory.service.impl;

import com.badlogic.gdx.utils.Json;
import io.github.minemon.NetworkProtocol;
import io.github.minemon.event.EventBus;
import io.github.minemon.inventory.event.InventoryChangeEvent;
import io.github.minemon.inventory.event.ItemUsedEvent;
import io.github.minemon.inventory.model.InventoryItem;
import io.github.minemon.inventory.model.InventorySlot;
import io.github.minemon.inventory.service.InventoryService;
import io.github.minemon.multiplayer.service.MultiplayerClient;
import io.github.minemon.player.service.PlayerService;
import io.github.minemon.world.service.WorldService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class InventoryServiceImpl implements InventoryService {
    private final int DEFAULT_INVENTORY_SIZE = 36;

    @Getter
    private final Map<String, InventoryItem> itemRegistry = new HashMap<>();
    private final List<InventorySlot> slots = new ArrayList<>(DEFAULT_INVENTORY_SIZE);
    @Autowired
    @Lazy
    private PlayerService playerService;
    @Autowired
    @Lazy
    private WorldService worldService;

    @Autowired
    @Lazy
    private MultiplayerClient multiplayerClient;
    @Autowired
    @Lazy
    private EventBus eventBus;
    public InventoryServiceImpl() {
        for (int i = 0; i < DEFAULT_INVENTORY_SIZE; i++) {
            slots.add(new InventorySlot());
        }
        registerItem(new InventoryItem("wooden_axe", "Wooden Axe"));
        registerItem(new InventoryItem("stick", "Stick"));
        registerItem(new InventoryItem("pokeball", "Pokeball"));
        registerItem(new InventoryItem("potion", "Potion"));
    }

    public void addItem(String itemId, int count) {
        String username = worldService.getPlayerData("").getUsername();
        boolean success = addItemInternal(itemId, count);

        if (success) {

            eventBus.fireEvent(new InventoryChangeEvent(
                username,
                findFirstSlotWithItem(itemId),
                itemId,
                count,
                InventoryChangeEvent.ChangeType.ITEM_ADDED
            ));


            if (worldService.isMultiplayerMode() && multiplayerClient.isConnected()) {
                NetworkProtocol.InventoryAction action = new NetworkProtocol.InventoryAction();
                action.setType(NetworkProtocol.InventoryActionType.ADD_ITEM);
                action.setItemId(itemId);
                action.setCount(count);
                multiplayerClient.sendMessage(action);
            }
        }

    }
    private int findFirstSlotWithItem(String itemId) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getItemId() != null && slots.get(i).getItemId().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }
    private boolean addItemInternal(String itemId, int count) {
        InventoryItem itemDef = itemRegistry.get(itemId);
        if (itemDef == null) {
            log.error("Attempted to add unknown item: {}", itemId);
            return false;
        }


        if (itemDef.isStackable()) {
            for (InventorySlot slot : slots) {
                if (slot.getItemId() != null && slot.getItemId().equals(itemId)) {
                    int space = itemDef.getMaxStackSize() - slot.getCount();
                    if (space > 0) {
                        int toAdd = Math.min(space, count);
                        slot.setCount(slot.getCount() + toAdd);
                        count -= toAdd;
                        if (count <= 0) return true;
                    }
                }
            }
        }


        while (count > 0) {
            InventorySlot emptySlot = findEmptySlot();
            if (emptySlot == null) {
                log.warn("Inventory full, couldn't add all items");
                return false;
            }

            int stackSize = Math.min(count, itemDef.getMaxStackSize());
            emptySlot.setItemId(itemId);
            emptySlot.setCount(stackSize);
            if (itemDef.getMaxDurability() > 0) {
                emptySlot.setDurability(itemDef.getMaxDurability());
                emptySlot.setMaxDurability(itemDef.getMaxDurability());
            }
            count -= stackSize;
        }

        return true;
    }    private InventorySlot findEmptySlot() {
        for (InventorySlot slot : slots) {
            if (slot.getItemId() == null) {
                return slot;
            }
        }
        return null;
    }
    public boolean useItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return false;

        InventorySlot slot = slots.get(slotIndex);
        if (slot.getItemId() == null || slot.getCount() <= 0) return false;

        String username = worldService.getPlayerData("").getUsername();
        eventBus.fireEvent(new ItemUsedEvent(username, slot.getItemId(), slotIndex, slot.copy()));


        if (slot.getMaxDurability() > 0) {
            slot.setDurability(slot.getDurability() - 1);
            if (slot.getDurability() <= 0) {
                slot.setItemId(null);
                slot.setCount(0);
            }
        } else {

            slot.setCount(slot.getCount() - 1);
            if (slot.getCount() <= 0) {
                slot.setItemId(null);
            }
        }


        if (worldService.isMultiplayerMode()) {
            NetworkProtocol.InventoryAction action = new NetworkProtocol.InventoryAction();
            action.setType(NetworkProtocol.InventoryActionType.REMOVE_ITEM);
            action.setSlotIndex(slotIndex);
            action.setCount(1);
            multiplayerClient.sendMessage(action);
        }

        return true;
    }


    public void moveItem(int fromSlot, int toSlot) {
        if (!isValidSlot(fromSlot) || !isValidSlot(toSlot)) return;

        InventorySlot source = slots.get(fromSlot);
        InventorySlot target = slots.get(toSlot);

        String username = worldService.getPlayerData("").getUsername();


        if (target.getItemId() != null && target.getItemId().equals(source.getItemId())) {
            InventoryItem itemDef = itemRegistry.get(source.getItemId());
            if (itemDef != null && itemDef.isStackable()) {
                int totalCount = target.getCount() + source.getCount();
                if (totalCount <= itemDef.getMaxStackSize()) {
                    target.setCount(totalCount);
                    source.setItemId(null);
                    source.setCount(0);
                } else {
                    target.setCount(itemDef.getMaxStackSize());
                    source.setCount(totalCount - itemDef.getMaxStackSize());
                }
            }
        } else {

            InventorySlot temp = source.copy();
            source.copyFrom(target);
            target.copyFrom(temp);
        }

        eventBus.fireEvent(new InventoryChangeEvent(
            username,
            toSlot,
            target.getItemId(),
            target.getCount(),
            InventoryChangeEvent.ChangeType.ITEM_MOVED
        ));

        if (worldService.isMultiplayerMode()) {
            NetworkProtocol.InventoryAction action = new NetworkProtocol.InventoryAction();
            action.setType(NetworkProtocol.InventoryActionType.MOVE_ITEM);
            action.setSlotIndex(fromSlot);
            action.setCount(toSlot);
            multiplayerClient.sendMessage(action);
        }
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < slots.size();
    }
    @Override
    public void registerItem(InventoryItem item) {
        itemRegistry.put(item.getItemId(), item);
    }


    @Override
    public boolean removeItem(String itemId, int count) {
        int remaining = count;
        for (InventorySlot slot : slots) {
            if (slot.getItemId() != null && slot.getItemId().equals(itemId)) {
                if (slot.getCount() <= remaining) {
                    remaining -= slot.getCount();
                    slot.setItemId(null);
                    slot.setCount(0);
                } else {
                    slot.setCount(slot.getCount() - remaining);
                    return true;
                }
            }
            return remaining <= 0;
        }
        return false;
    }

    @Override
    public List<InventorySlot> getInventory() {
        return Collections.unmodifiableList(slots);
    }

    @Override
    public String serializeInventory() {
        Json json = new Json();

        return json.toJson(slots);
    }

    public void deserializeInventory(String data) {
        if (data == null || data.isEmpty()) return;

        Json json = new Json();
        List<InventorySlot> loaded = json.fromJson(ArrayList.class, InventorySlot.class, data);

        String username = worldService.getPlayerData("").getUsername();
        slots.clear();
        slots.addAll(loaded);
        while (slots.size() < DEFAULT_INVENTORY_SIZE) {
            slots.add(new InventorySlot());
        }

        eventBus.fireEvent(new InventoryChangeEvent(
            username,
            -1,
            null,
            -1,
            InventoryChangeEvent.ChangeType.INVENTORY_LOADED
        ));
    }

}
