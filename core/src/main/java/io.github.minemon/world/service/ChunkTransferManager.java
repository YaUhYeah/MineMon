package io.github.minemon.world.service;

import io.github.minemon.NetworkProtocol;
import io.github.minemon.world.model.WorldObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChunkTransferManager {
    private static final int MAX_OBJECTS_PER_PACKET = 50;

    public List<NetworkProtocol.ChunkData> prepareChunkTransfer(int chunkX, int chunkY, int[][] tiles, List<WorldObject> objects) {
        List<NetworkProtocol.ChunkData> packets = new ArrayList<>();

        // Calculate how many packets we need based on object count
        int totalObjects = objects != null ? objects.size() : 0;
        int totalPackets = (totalObjects + MAX_OBJECTS_PER_PACKET - 1) / MAX_OBJECTS_PER_PACKET;
        totalPackets = Math.max(1, totalPackets); // At least one packet even if no objects

        for (int i = 0; i < totalPackets; i++) {
            NetworkProtocol.ChunkData packet = new NetworkProtocol.ChunkData();
            packet.setChunkX(chunkX);
            packet.setChunkY(chunkY);

            // Only send tiles in first packet
            if (i == 0) {
                packet.setTiles(tiles);
            }

            // Calculate object slice for this packet
            if (objects != null && !objects.isEmpty()) {
                int startIndex = i * MAX_OBJECTS_PER_PACKET;
                int endIndex = Math.min(startIndex + MAX_OBJECTS_PER_PACKET, objects.size());
                if (startIndex < objects.size()) {
                    packet.setObjects(new ArrayList<>(objects.subList(startIndex, endIndex)));
                } else {
                    packet.setObjects(new ArrayList<>());
                }
            } else {
                packet.setObjects(new ArrayList<>());
            }

            packet.setPartial(totalPackets > 1);
            packet.setPartNumber(i);
            packet.setTotalParts(totalPackets);

            packets.add(packet);
            log.debug("Prepared chunk packet {}/{} for chunk ({},{})",
                i + 1, totalPackets, chunkX, chunkY);
        }

        return packets;
    }
}
