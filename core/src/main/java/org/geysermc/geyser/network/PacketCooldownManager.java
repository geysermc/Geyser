package org.geysermc.geyser.network;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockPickRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.BookEditPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityPickRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.FilterTextPacket;
import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Map;

public class PacketCooldownManager {

    private final GeyserSession session;
    private final Map<String, CooldownSettings> packetCooldownSettings = new Object2ObjectOpenHashMap<>();
    private final Map<String, CooldownTracker> activeCooldowns = new Object2ObjectOpenHashMap<>();

    public PacketCooldownManager(GeyserSession session) {
        this.session = session;

        setPacketCooldown(LoginPacket.class, -1, 2);
        setPacketCooldown(ResourcePackClientResponsePacket.class, -1, 4);
        setPacketCooldown(ResourcePackChunkRequestPacket.class, -1, 0);
        setPacketCooldown(RequestNetworkSettingsPacket.class, -1, 2);
        setPacketCooldown(TextPacket.class, 1000, 50);
        setPacketCooldown(CommandRequestPacket.class, 1000, 50);
        setPacketCooldown(ModalFormResponsePacket.class, 1000, 50);
        setPacketCooldown(BlockEntityDataPacket.class, 1000, 40);
        setPacketCooldown(SetLocalPlayerAsInitializedPacket.class, 1000, 40);
        setPacketCooldown(BlockPickRequestPacket.class, 1000, 40);
        setPacketCooldown(EntityPickRequestPacket.class, 1000, 40);
        setPacketCooldown(BookEditPacket.class, 1000, 40);
        setPacketCooldown(FilterTextPacket.class, 1000, 40);
        setPacketCooldown(LecternUpdatePacket.class, 1000, 10);
    }

    public void setPacketCooldown(Class<? extends BedrockPacket> packetClass, int cooldownMillis, int maxCount) {
        packetCooldownSettings.put(packetClass.getSimpleName(), new CooldownSettings(cooldownMillis, maxCount));
    }

    private boolean isCooldown(BedrockPacket packet) {
        String packetName = packet.getClass().getSimpleName();
        CooldownSettings settings = packetCooldownSettings.get(packetName);
        if (settings == null) return false;

        CooldownTracker tracker = activeCooldowns.computeIfAbsent(packetName, k -> {
            CooldownTracker newTracker = new CooldownTracker();
            long cooldownMillis = settings.cooldownMillis();
            if (cooldownMillis == -1) {
                newTracker.setExpiryTime(-1);
            } else {
                newTracker.setExpiryTime(System.currentTimeMillis() + cooldownMillis);
            }
            return newTracker;
        });
        tracker.incrementCount();

        if (tracker.getExpiryTime() != -1 && tracker.getExpiryTime() <= System.currentTimeMillis()) {
            activeCooldowns.remove(packetName);
            return false;
        }

        return tracker.getCount() >= settings.maxCount();
    }

    public void handle(BedrockPacket packet) {
        String packetName = packet.getClass().getSimpleName();
        if (!isCooldown(packet)) return;
        if (session.getGeyser().getConfig().isDebugMode()) {
            CooldownTracker tracker = activeCooldowns.get(packetName);
            String message = session.getSocketAddress().getAddress().toString() + " -> Attempted to send too many packets " + packetName + " count " + tracker.getCount();
            if (session.isLoggedIn()) {
                message += " by user " + session.bedrockUsername();
            }
            session.getGeyser().getLogger().debug(message);
        }
        session.disconnect("many Packets " + packetName);
    }

    private record CooldownSettings(int cooldownMillis, int maxCount) {
    }

    @Getter
    private class CooldownTracker {
        private long count;
        @Setter
        private long expiryTime;

        public void incrementCount() {
            this.count++;
        }
    }
}
