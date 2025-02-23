/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.platform.mod;

import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerStatusPacketListener;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.ping.GeyserPingInfo;
import org.geysermc.geyser.ping.IGeyserPingPassthrough;

import java.net.InetSocketAddress;
import java.util.Objects;

@AllArgsConstructor
public class ModPingPassthrough implements IGeyserPingPassthrough {

    private static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final MinecraftServer server;
    private final GeyserLogger logger;

    @Nullable
    @Override
    public GeyserPingInfo getPingInformation(InetSocketAddress inetSocketAddress) {
        ServerStatus status = server.getStatus();
        if (status == null) {
            return null;
        }

        try {
            StatusInterceptor connection = new StatusInterceptor();
            ServerStatusPacketListener statusPacketListener = new ServerStatusPacketListenerImpl(status, connection);

            statusPacketListener.handleStatusRequest(ServerboundStatusRequestPacket.INSTANCE);
            // mods like MiniMOTD (that inject into the above method) have now processed the response
            status = Objects.requireNonNull(connection.status, "status response");
        } catch (Exception e) {
            if (logger.isDebug()) {
                logger.debug("Failed to listen for modified ServerStatus: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return new GeyserPingInfo(
            net.minecraft.network.chat.Component.Serializer.toJson(status.description(), RegistryAccess.EMPTY),
            status.players().map(ServerStatus.Players::max).orElse(1),
            status.players().map(ServerStatus.Players::online).orElse(0)
        );
    }

    /**
     * Custom Connection that intercepts the status response right before it is sent
     */
    private static class StatusInterceptor extends Connection {

        ServerStatus status;

        StatusInterceptor() {
            super(PacketFlow.SERVERBOUND); // we are the server.
        }

        @Override
        public void send(@NonNull Packet<?> packet, @Nullable PacketSendListener packetSendListener, boolean bl) {
            if (packet instanceof ClientboundStatusResponsePacket statusResponse) {
                status = statusResponse.status();
            }
            super.send(packet, packetSendListener, bl);
        }
    }
}
