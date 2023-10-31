/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.platform.velocity;

import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.ping.GeyserPingInfo;
import org.geysermc.geyser.ping.IGeyserPingPassthrough;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@AllArgsConstructor
public class GeyserVelocityPingPassthrough implements IGeyserPingPassthrough {

    private final ProxyServer server;

    @Override
    public GeyserPingInfo getPingInformation(InetSocketAddress inetSocketAddress) {
        ProxyPingEvent event;
        try {
            event = server.getEventManager().fire(new ProxyPingEvent(new GeyserInboundConnection(inetSocketAddress), ServerPing.builder()
                    .description(server.getConfiguration().getMotd()).onlinePlayers(server.getPlayerCount())
                    .maximumPlayers(server.getConfiguration().getShowMaxPlayers()).build())).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Apparently, plugins such as MiniMOTD can just yeet all players
        int maxPlayers;
        int onlinePlayers;
        if (event.getPing().getPlayers().isPresent()) {
            maxPlayers = event.getPing().getPlayers().get().getMax();
            onlinePlayers = event.getPing().getPlayers().get().getOnline();
        } else {
            maxPlayers = GeyserImpl.getInstance().getConfig().getMaxPlayers(); // Gotta fallback to something
            onlinePlayers = GeyserImpl.getInstance().getSessionManager().size();
        }

        GeyserPingInfo geyserPingInfo = new GeyserPingInfo(
                LegacyComponentSerializer.legacy('§').serialize(event.getPing().getDescriptionComponent()),
                new GeyserPingInfo.Players(maxPlayers, onlinePlayers),
                new GeyserPingInfo.Version(
                        event.getPing().getVersion().getName(),
                        event.getPing().getVersion().getProtocol()
                )
        );
        event.getPing().getPlayers().get().getSample().stream().map(ServerPing.SamplePlayer::getName).forEach(geyserPingInfo.getPlayerList()::add);
        return geyserPingInfo;
    }

    private static class GeyserInboundConnection implements InboundConnection {

        private final InetSocketAddress remote;

        public GeyserInboundConnection(InetSocketAddress remote) {
            this.remote = remote;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return this.remote;
        }

        @Override
        public Optional<InetSocketAddress> getVirtualHost() {
            return Optional.empty();
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return ProtocolVersion.MAXIMUM_VERSION;
        }
    }

}
