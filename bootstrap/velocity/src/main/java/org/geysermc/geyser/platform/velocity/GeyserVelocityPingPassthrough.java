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
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.proxy.server.ServerPing.Version;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.geysermc.geyser.network.GameProtocol;
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
                    .maximumPlayers(server.getConfiguration().getShowMaxPlayers())
                    .version(new Version(GameProtocol.getJavaProtocolVersion(), GameProtocol.getJavaMinecraftVersion())) 
                    .build())).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return new GeyserPingInfo(
                GsonComponentSerializer.gson().serialize(event.getPing().getDescriptionComponent()),
                event.getPing().getPlayers().map(ServerPing.Players::getMax).orElse(1),
                event.getPing().getPlayers().map(ServerPing.Players::getOnline).orElse(0)
        );
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

        @Override
        public ProtocolState getProtocolState() {
            return ProtocolState.STATUS;
        }
    }

}
