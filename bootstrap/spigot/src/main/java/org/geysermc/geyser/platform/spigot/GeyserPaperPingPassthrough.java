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

package org.geysermc.geyser.platform.spigot;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.network.StatusClient;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.ping.GeyserPingInfo;
import org.geysermc.geyser.ping.IGeyserPingPassthrough;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;

/**
 * This class is used if possible, so listeners listening for PaperServerListPingEvent exclusively have their changes
 * applied.
 */
public final class GeyserPaperPingPassthrough implements IGeyserPingPassthrough {
    private static final Constructor<PaperServerListPingEvent> OLD_CONSTRUCTOR = ReflectedNames.getOldPaperPingConstructor();

    private final GeyserSpigotLogger logger;

    public GeyserPaperPingPassthrough(GeyserSpigotLogger logger) {
        this.logger = logger;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    @Override
    public GeyserPingInfo getPingInformation(InetSocketAddress inetSocketAddress) {
        try {
            // We'd rather *not* use deprecations here, but unfortunately any Adventure class would be relocated at
            // runtime because we still have to shade in our own Adventure class. For now.
            PaperServerListPingEvent event;
            if (OLD_CONSTRUCTOR != null) {
                // 1.19, removed in 1.19.4
                event = OLD_CONSTRUCTOR.newInstance(new GeyserStatusClient(inetSocketAddress),
                        Bukkit.getMotd(), Bukkit.getOnlinePlayers().size(),
                        Bukkit.getMaxPlayers(), Bukkit.getVersion(), GameProtocol.getJavaProtocolVersion(), null);
            } else {
                event = new PaperServerListPingEvent(new GeyserStatusClient(inetSocketAddress),
                        Bukkit.getMotd(), Bukkit.getOnlinePlayers().size(),
                        Bukkit.getMaxPlayers(), Bukkit.getVersion(), GameProtocol.getJavaProtocolVersion(), null);
            }
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                // We have to send a ping, so not really sure what else to do here.
                return null;
            }

            GeyserPingInfo.Players players;
            if (event.shouldHidePlayers()) {
                players = new GeyserPingInfo.Players(1, 0);
            } else {
                players = new GeyserPingInfo.Players(event.getMaxPlayers(), event.getNumPlayers());
            }

            return new GeyserPingInfo(
                GsonComponentSerializer.gson().serialize(LegacyComponentSerializer.legacySection().deserialize(event.getMotd())),
                players
            );
        } catch (Exception | LinkageError e) { // LinkageError in the event that method/constructor signatures change
            logger.debug("Error while getting Paper ping passthrough: " + e);
            return null;
        }
    }

    private record GeyserStatusClient(InetSocketAddress address) implements StatusClient {
        @Override
        public @NonNull InetSocketAddress getAddress() {
            return address;
        }

        @Override
        public int getProtocolVersion() {
            return GameProtocol.getJavaProtocolVersion();
        }

        @Override
        public @Nullable InetSocketAddress getVirtualHost() {
            return null;
        }
    }
}
