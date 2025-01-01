package io.github.minemon.audio.model;

import lombok.Getter;

@Getter
public enum SoundEffect {
    ITEM_PICKUP("sounds/pickup.ogg"),
    MENU_SELECT("sounds/select.ogg"),
    MENU_BACK("sounds/back.ogg"),
    BATTLE_WIN("sounds/battle_win.ogg"),
    CRITICAL_HIT("sounds/critical_hit.ogg"),
    CURSOR_MOVE("sounds/cursor_move.ogg"),
    DAMAGE("sounds/damage.ogg"),
    COLLIDE("sounds/player-bump.ogg"),
    MOVE_SELECT("sounds/move_select.ogg"),
    NOT_EFFECTIVE("sounds/not_effective.ogg"),
    SUPER_EFFECTIVE("sounds/super_effective.ogg"),
    CRAFT("sounds/crafting.ogg"),
    BLOCK_PLACE_0("sounds/block_place_0.ogg"),
    BLOCK_PLACE_1("sounds/block_place_1.ogg"),
    BLOCK_PLACE_2("sounds/block_place_2.ogg"),
    BLOCK_BREAK_WOOD("sounds/break_wood.ogg"),
    TOOL_BREAK("sounds/tool_break.ogg"),
    BLOCK_BREAK_WOOD_HAND("sounds/break_wood_hand.ogg"),
    PUDDLE("sounds/puddle.ogg"),
    CHEST_OPEN("sounds/chest-open.ogg"),
    CHEST_CLOSE("sounds/chest-close.ogg"),
    HOUSE_BUILD("sounds/house_build.ogg");

    private final String path;

    SoundEffect(String path) {
        this.path = path;
    }

}
