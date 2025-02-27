package io.github.minemon;

import com.esotericsoftware.kryo.Kryo;
import io.github.minemon.chat.model.ChatMessage;
import io.github.minemon.inventory.model.InventorySlot;
import io.github.minemon.player.model.PlayerData;
import io.github.minemon.utils.UUIDSerializer;
import io.github.minemon.world.model.WorldObject;
import io.github.minemon.multiplayer.model.PlayerSyncData;
import io.github.minemon.multiplayer.model.WorldObjectUpdate;
import lombok.Data;

import java.util.*;

public final class NetworkProtocol {

    private NetworkProtocol() {
    }

    public static void registerClasses(Kryo kryo) {
        kryo.register(UUID.class, new UUIDSerializer());
        kryo.register(java.util.UUID.class);
        kryo.register(ChatMessage.class);

        kryo.register(UUID.class, new UUIDSerializer());
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(HashSet.class);
        kryo.register(WorldObject.class);
        kryo.register(WorldObject[].class);
        kryo.register(ChunkRequestAck.class);
        kryo.register(ArrayList.class);
        kryo.register(int[].class);
        kryo.register(int[][].class);
        kryo.register(ChunkData.class);
        kryo.register(PlayerData.class);
        kryo.register(WorldObject.class);
        kryo.register(PlayerSyncData.class);
        kryo.register(WorldObjectUpdate.class);

        kryo.register(ServerShutdownNotice.class);

        kryo.register(List.class);
        kryo.register(Set.class);
        kryo.register(InventorySlot.class);
        kryo.register(InventoryUpdate.class);
        kryo.register(InventoryAction.class);
        kryo.register(InventoryActionType.class);

        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(PlayerMoveRequest.class);
        kryo.register(PlayerStatesUpdate.class);
        kryo.register(ChunkRequest.class);
        kryo.register(ChunkData.class);
        kryo.register(WorldObjectsUpdate.class);

        kryo.register(CreateUserRequest.class);
        kryo.register(CreateUserResponse.class);

        kryo.setRegistrationRequired(false);
        kryo.setReferences(false);
    }

    public enum InventoryActionType {
        ADD_ITEM,
        REMOVE_ITEM,
        MOVE_ITEM,
        SYNC_REQUEST
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private long timestamp;
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
    }

    @Data
    public static class ChunkRequestAck {
        private int chunkX;
        private int chunkY;
    }

    @Data
    public static class CreateUserResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class ServerShutdownNotice {
        private String message;
        private ShutdownReason reason;

        public enum ShutdownReason {
            NORMAL_SHUTDOWN,
            TIMEOUT,
            ERROR
        }
    }

    @Data
    public static class InventoryUpdate {
        private String username;
        private String serializedInventory;
    }

    @Data
    public static class InventoryAction {
        private String username;
        private InventoryActionType type;
        private String itemId;
        private int slotIndex;
        private int count;
    }

    @Data
    public static class LoginResponse {
        private boolean success;
        private String message;
        private String username;
        private int x;
        private int y;
        private long timestamp;
        private String inventoryData;
    }

    @Data
    public static class PlayerMoveRequest {
        private float x;
        private float y;
        private boolean running;
        private boolean moving;
        private String direction;
    }

    @Data
    public static class PlayerStatesUpdate {
        private Map<String, PlayerSyncData> players;
    }

    @Data
    public static class ChunkRequest {
        private int chunkX;
        private int chunkY;
        private long timestamp;
    }

    @Data
    public static class ChunkData {
        private int chunkX;
        private int chunkY;
        private int[][] tiles;
        private List<WorldObject> objects = new ArrayList<>();
        private boolean isPartial;
        private int partNumber;
        private int totalParts;
    }

    @Data
    public static class WorldObjectsUpdate {
        private List<WorldObjectUpdate> objects;
    }
}
