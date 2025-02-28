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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.network.netty.GeyserInjector;
import org.geysermc.geyser.network.netty.LocalServerChannelWrapper;
import org.geysermc.geyser.platform.mod.platform.GeyserModPlatform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class GeyserModInjector extends GeyserInjector {

    private final MinecraftServer server;
    private final GeyserModPlatform platform;
    private DefaultEventLoopGroup eventLoopGroup;

    /**
     * Used to uninject ourselves on shutdown.
     */
    private List<ChannelFuture> allServerChannels;

    public GeyserModInjector(MinecraftServer server, GeyserModPlatform platform) {
        this.server = server;
        this.platform = platform;
    }

    @Override
    protected void initializeLocalChannel0(GeyserBootstrap bootstrap) throws Exception {
        ServerConnectionListener connection = this.server.getConnection();

        // Find the channel that Minecraft uses to listen to connections
        ChannelFuture listeningChannel = null;
        this.allServerChannels = ((GeyserChannelGetter) connection).geyser$getChannels();
        for (ChannelFuture o : allServerChannels) {
            listeningChannel = o;
            break;
        }

        if (listeningChannel == null) {
            throw new RuntimeException("Unable to find listening channel!");
        }

        // Making this a function prevents childHandler from being treated as a non-final variable
        ChannelInitializer<Channel> childHandler = getChildHandler(bootstrap, listeningChannel);
        // This method is what initializes the connection in Java Edition, after Netty is all set.
        Method initChannel = childHandler.getClass().getDeclaredMethod("initChannel", Channel.class);
        initChannel.setAccessible(true);

        // Separate variable so we can shut it down later
        eventLoopGroup = new DefaultEventLoopGroup(0, new DefaultThreadFactory("Geyser " + this.platform.platformType().platformName() + " connection thread", Thread.MAX_PRIORITY));
        ChannelFuture channelFuture = (new ServerBootstrap()
                .channel(LocalServerChannelWrapper.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        initChannel.invoke(childHandler, ch);

                        int index = ch.pipeline().names().indexOf("encoder");
                        String baseName = index != -1 ? "encoder" : "outbound_config";

                        if (bootstrap.config().advanced().disableCompression()) {
                            ch.pipeline().addAfter(baseName, "geyser-compression-disabler", new GeyserModCompressionDisabler());
                        }
                    }
                })
                // Set to MAX_PRIORITY as MultithreadEventLoopGroup#newDefaultThreadFactory which DefaultEventLoopGroup implements does by default
                .group(eventLoopGroup)
                .localAddress(LocalAddress.ANY))
                .bind()
                .syncUninterruptibly();
        // We don't need to add to the list, but plugins like ProtocolSupport and ProtocolLib that add to the main pipeline
        // will work when we add to the list.
        allServerChannels.add(channelFuture);
        this.localChannel = channelFuture;
        this.serverSocketAddress = channelFuture.channel().localAddress();
    }

    @SuppressWarnings("unchecked")
    private ChannelInitializer<Channel> getChildHandler(GeyserBootstrap bootstrap, ChannelFuture listeningChannel) {
        List<String> names = listeningChannel.channel().pipeline().names();
        ChannelInitializer<Channel> childHandler = null;
        for (String name : names) {
            ChannelHandler handler = listeningChannel.channel().pipeline().get(name);
            try {
                Field childHandlerField = handler.getClass().getDeclaredField("childHandler");
                childHandlerField.setAccessible(true);
                childHandler = (ChannelInitializer<Channel>) childHandlerField.get(handler);
                break;
            } catch (Exception e) {
                if (bootstrap.config().debugMode()) {
                    bootstrap.getGeyserLogger().debug("The handler " + name + " isn't a ChannelInitializer. THIS ERROR IS SAFE TO IGNORE!");
                    e.printStackTrace();
                }
            }
        }
        if (childHandler == null) {
            throw new RuntimeException();
        }
        return childHandler;
    }

    @Override
    public void shutdown() {
        if (this.allServerChannels != null) {
            this.allServerChannels.remove(this.localChannel);
            this.allServerChannels = null;
        }

        if (eventLoopGroup != null) {
            try {
                eventLoopGroup.shutdownGracefully().sync();
                eventLoopGroup = null;
            } catch (Exception e) {
                GeyserImpl.getInstance().getLogger().error("Unable to shut down injector! " + e.getMessage());
                e.printStackTrace();
            }
        }

        super.shutdown();
    }
}
