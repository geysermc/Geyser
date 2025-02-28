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

package org.geysermc.geyser.network.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.unix.PreferredDirectByteBufAllocator;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.mcprotocollib.network.helper.NettyHelper;
import org.geysermc.mcprotocollib.network.netty.MinecraftChannelInitializer;
import org.geysermc.mcprotocollib.network.packet.PacketProtocol;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Manages a Minecraft Java session over our LocalChannel implementations.
 */
public final class LocalSession extends ClientNetworkSession {
    private static DefaultEventLoopGroup DEFAULT_EVENT_LOOP_GROUP;
    private static PreferredDirectByteBufAllocator PREFERRED_DIRECT_BYTE_BUF_ALLOCATOR = null;

    private final SocketAddress spoofedRemoteAddress;

    public LocalSession(SocketAddress targetAddress, String clientIp, MinecraftProtocol protocol, Executor packetHandlerExecutor) {
        super(targetAddress, protocol, packetHandlerExecutor, null, null);
        this.spoofedRemoteAddress = new InetSocketAddress(clientIp, 0);
    }

    @Override
    protected ChannelFactory<? extends Channel> getChannelFactory() {
        return new ReflectiveChannelFactory<>(LocalChannelWithRemoteAddress.class);
    }

    @Override
    protected void setOptions(Bootstrap bootstrap) {
        if (PREFERRED_DIRECT_BYTE_BUF_ALLOCATOR != null) {
            bootstrap.option(ChannelOption.ALLOCATOR, PREFERRED_DIRECT_BYTE_BUF_ALLOCATOR);
        }
    }

    @Override
    protected EventLoopGroup getEventLoopGroup() {
        if (DEFAULT_EVENT_LOOP_GROUP == null) {
            DEFAULT_EVENT_LOOP_GROUP = new DefaultEventLoopGroup(new DefaultThreadFactory(this.getClass(), true));
            Runtime.getRuntime().addShutdownHook(new Thread(
                () -> DEFAULT_EVENT_LOOP_GROUP.shutdownGracefully(100, 500, TimeUnit.MILLISECONDS)));
        }

        return DEFAULT_EVENT_LOOP_GROUP;
    }

    @Override
    protected ChannelHandler getChannelHandler() {
        return new MinecraftChannelInitializer<>(channel -> {
            PacketProtocol protocol = getPacketProtocol();
            protocol.newClientSession(LocalSession.this);

            return LocalSession.this;
        }, true) {
            @Override
            public void initChannel(@NonNull Channel channel) throws Exception {
                ((LocalChannelWithRemoteAddress) channel).spoofedRemoteAddress(spoofedRemoteAddress);

                NettyHelper.initializeHAProxySupport(LocalSession.this, channel);

                super.initChannel(channel);
            }
        };
    }

    /**
     * Should only be called when direct ByteBufs should be preferred. At this moment, this should only be called on BungeeCord.
     */
    public static void createDirectByteBufAllocator() {
        if (PREFERRED_DIRECT_BYTE_BUF_ALLOCATOR == null) {
            PREFERRED_DIRECT_BYTE_BUF_ALLOCATOR = new PreferredDirectByteBufAllocator();
            PREFERRED_DIRECT_BYTE_BUF_ALLOCATOR.updateAllocator(ByteBufAllocator.DEFAULT);
        }
    }
}
