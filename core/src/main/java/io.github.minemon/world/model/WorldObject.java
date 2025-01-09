package io.github.minemon.world.model;

import com.badlogic.gdx.math.Rectangle;

import lombok.Data;

import java.util.UUID;


@Data
public class WorldObject {

    private String id;

    private int tileX;
    private int tileY;

    private ObjectType type;

    private float spawnTime;
    private boolean collidable;


    private float timeSinceVisible;

    public WorldObject() {

    }

    public WorldObject(int tileX, int tileY, ObjectType type, boolean collidable) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.type = type;
        this.collidable = collidable;
        this.id = UUID.randomUUID().toString();
        this.spawnTime = type.isPermanent() ? 0f : (System.currentTimeMillis() / 1000f);
        this.timeSinceVisible = 0f;
    }



    public float getFadeAlpha() {
        return Math.min(timeSinceVisible, 1f);
    }


    public Rectangle getCollisionBox() {
        if (!collidable) return null;

        float pixelX = tileX * 32;
        float pixelY = tileY * 32;

        if (type == ObjectType.APRICORN_TREE) {
            float width  = 32;
            float height = 64;

            float offsetX = 32;
            float offsetY = 0;

            return new Rectangle(
                pixelX + offsetX,
                pixelY + offsetY,
                width,
                height
            );

        } else if (isTreeType(type)) {
            return new Rectangle(
                pixelX,
                pixelY,
                2 * 32,
                2 * 32
            );
        }

        return new Rectangle(
            pixelX,
            pixelY,
            type.getWidthInTiles() * 32,
            type.getHeightInTiles() * 32
        );
    }

    private boolean isTreeType(ObjectType t) {
        return t == ObjectType.TREE_0 ||
                t == ObjectType.TREE_1 ||
                t == ObjectType.SNOW_TREE ||
                t == ObjectType.HAUNTED_TREE ||
                t == ObjectType.RUINS_TREE ||
                t == ObjectType.APRICORN_TREE ||
                t == ObjectType.RAIN_TREE ||
                t == ObjectType.CHERRY_TREE;
    }
}
