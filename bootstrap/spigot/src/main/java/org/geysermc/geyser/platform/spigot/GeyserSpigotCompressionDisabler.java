/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.geysermc.geyser.GeyserLogger;

/**
 * Disables the compression packet (and the compression handlers from being added to the pipeline) for Geyser clients
 * that won't be receiving the data over the network.
 * <p>
 * As of 1.8 - 1.17.1, compression is enabled in the Netty pipeline by adding a listener after a packet is written.
 * If we simply "cancel" or don't forward the packet, then the listener is never called.
 */
public class GeyserSpigotCompressionDisabler extends ChannelOutboundHandlerAdapter {
    static final boolean ENABLED;

    private static final Class<?> COMPRESSION_PACKET_CLASS;
    private static final Class<?> LOGIN_SUCCESS_PACKET_CLASS;
    private static final boolean PROTOCOL_SUPPORT_INSTALLED;

    static {
        PROTOCOL_SUPPORT_INSTALLED = Bukkit.getPluginManager().getPlugin("ProtocolSupport") != null;

        Class<?> compressionPacketClass = null;
        Class<?> loginSuccessPacketClass = null;
        boolean enabled = false;
        try {
            compressionPacketClass = findCompressionPacket();
            loginSuccessPacketClass = findLoginSuccessPacket();
            enabled = true;
        } catch (Exception e) {
            GeyserLogger.get().error("Could not initialize compression disabler!", e);
        }
        COMPRESSION_PACKET_CLASS = compressionPacketClass;
        LOGIN_SUCCESS_PACKET_CLASS = loginSuccessPacketClass;
        ENABLED = enabled;
    }

    public GeyserSpigotCompressionDisabler() {
        if (!ENABLED) {
            throw new RuntimeException("Geyser compression disabler cannot be initialized in its current state!");
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        Class<?> msgClass = msg.getClass();
        // Don't let any compression packet get through
        if (!COMPRESSION_PACKET_CLASS.isAssignableFrom(msgClass)) {
            if (LOGIN_SUCCESS_PACKET_CLASS.isAssignableFrom(msgClass)) {
                if (PROTOCOL_SUPPORT_INSTALLED) {
                    // ProtocolSupport must send the compression packet, so let's remove what it did before it does damage
                    if (ctx.pipeline().get("compress") != null) {
                        ctx.pipeline().remove("compress");
                    }
                    if (ctx.pipeline().get("decompress") != null) {
                        ctx.pipeline().remove("decompress");
                    }
                }
                // We're past the point that a compression packet can be sent, so we can safely yeet ourselves away
                ctx.channel().pipeline().remove(this);
            }
            super.write(ctx, msg, promise);
        } else if (PROTOCOL_SUPPORT_INSTALLED) {
            // We must indicate it "succeeded" or ProtocolSupport will time us out
            promise.setSuccess();
        }
    }

    private static Class<?> findCompressionPacket() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.network.protocol.login.PacketLoginOutSetCompression");
        } catch (ClassNotFoundException e) {
            String prefix = Bukkit.getServer().getClass().getPackage().getName().replace("org.bukkit.craftbukkit", "net.minecraft.server");
            return Class.forName(prefix + ".PacketLoginOutSetCompression");
        }
    }

    private static Class<?> findLoginSuccessPacket() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.network.protocol.login.PacketLoginOutSuccess");
        } catch (ClassNotFoundException e) {
            String prefix = Bukkit.getServer().getClass().getPackage().getName().replace("org.bukkit.craftbukkit", "net.minecraft.server");
            return Class.forName(prefix + ".PacketLoginOutSuccess");
        }
    }
}
