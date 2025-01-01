package io.github.minemon.world.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class TileConfig {
    private List<TileDefinition> tiles;

    @Setter
    @Getter
    public static class TileDefinition {
        private int id;
        private String name;
        private String texture;
        private boolean passable;

    }
}
