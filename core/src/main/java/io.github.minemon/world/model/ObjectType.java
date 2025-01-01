package io.github.minemon.world.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ObjectType {

    TREE_0(true, true, 2, 3, RenderLayer.LAYERED, "treeONE"),
    TREE_1(true, true, 2, 3, RenderLayer.LAYERED, "treeTWO"),
    SNOW_TREE(true, true, 2, 3, RenderLayer.LAYERED, "snow_tree"),
    HAUNTED_TREE(true, true, 2, 3, RenderLayer.LAYERED, "haunted_tree"),
    RUINS_TREE(true, true, 2, 3, RenderLayer.LAYERED, "ruins_tree"),
    APRICORN_TREE(true, true, 3, 3, RenderLayer.LAYERED, "apricorn_tree_grown"),


    CACTUS(true, true, 1, 2, RenderLayer.BELOW_PLAYER, "desert_cactus"),
    DEAD_TREE(true, true, 1, 2, RenderLayer.BELOW_PLAYER, "dead_tree"),
    SMALL_HAUNTED_TREE(true, true, 1, 2, RenderLayer.BELOW_PLAYER, "small_haunted_tree"),
    BUSH(true, true, 3, 2, RenderLayer.BELOW_PLAYER, "bush"),
    VINES(true, false, 1, 2, RenderLayer.BELOW_PLAYER, "vines"),
    RUIN_POLE(true, true, 1, 3, RenderLayer.BELOW_PLAYER, "ruins_pole"),
    POKEBALL(true, true, 1, 1, RenderLayer.BELOW_PLAYER, "pokeball"),
    RAIN_TREE(true, true, 2, 3, RenderLayer.LAYERED, "rain_tree"),
    CHERRY_TREE(true, true, 2, 3, RenderLayer.LAYERED, "CherryTree"),
    SUNFLOWER(true, false, 1, 2, RenderLayer.BELOW_PLAYER, "sunflower");

    private final boolean isPermanent;
    private final boolean isCollidable;
    private final int widthInTiles;
    private final int heightInTiles;
    private final RenderLayer renderLayer;
    private final String textureRegionName;

    public enum RenderLayer {
        BELOW_PLAYER,
        ABOVE_PLAYER,
        LAYERED,
        ABOVE_TALL_GRASS
    }
}
