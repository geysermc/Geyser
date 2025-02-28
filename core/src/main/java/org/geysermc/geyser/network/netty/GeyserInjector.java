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

import io.netty.channel.ChannelFuture;
import lombok.Getter;
import org.geysermc.geyser.GeyserBootstrap;

import java.net.SocketAddress;

/**
 * Used to inject Geyser clients directly into the server, bypassing the need to implement a complete TCP connection,
 * by creating a local channel.
 */
public abstract class GeyserInjector {
    /**
     * The local channel we can use to inject ourselves into the server without creating a TCP connection.
     */
    protected ChannelFuture localChannel;
    /**
     * The LocalAddress to use to connect to the server without connecting over TCP.
     */
    @Getter
    protected SocketAddress serverSocketAddress;

    /**
     * @param bootstrap the bootstrap of the Geyser instance.
     */
    public void initializeLocalChannel(GeyserBootstrap bootstrap) {
        if (!bootstrap.config().advanced().useDirectConnection()) {
            bootstrap.getGeyserLogger().debug("Disabling direct injection!");
            return;
        }

        if (this.localChannel != null) {
            bootstrap.getGeyserLogger().warning("Geyser attempted to inject into the server connection handler twice! Please ensure you aren't using /reload or any plugin that (re)loads Geyser after the server has started.");
            return;
        }

        try {
            initializeLocalChannel0(bootstrap);
            bootstrap.getGeyserLogger().debug("Local injection succeeded!");
        } catch (Exception e) {
            e.printStackTrace();
            // If the injector partially worked, undo it
            shutdown();
        }
    }

    /**
     * The method to implement that is called by {@link #initializeLocalChannel(GeyserBootstrap)} wrapped around a try/catch.
     */
    protected abstract void initializeLocalChannel0(GeyserBootstrap bootstrap) throws Exception;

    public void shutdown() {
        if (localChannel != null && localChannel.channel().isOpen()) {
            try {
                localChannel.channel().close().sync();
                localChannel = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (localChannel != null) {
            localChannel = null;
        }
    }
}
