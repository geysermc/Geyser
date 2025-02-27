/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;

import java.util.stream.Stream;

@RequiredArgsConstructor
public class InvalidPacketHandler extends ChannelInboundHandlerAdapter {
    public static final String NAME = "rak-error-handler";

    private final GeyserSession session;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Throwable rootCause = Stream.iterate(cause, Throwable::getCause)
                .filter(element -> element.getCause() == null)
                .findFirst()
                .orElse(cause);

        GeyserLogger logger = GeyserLogger.get();

        if (!(rootCause instanceof IllegalArgumentException)) {
            // Kick users that cause exceptions
            logger.warning("Exception caught in session of" + session.bedrockUsername() + ": " + rootCause.getMessage());
            session.disconnect("An internal error occurred!");
            return;
        }

        // Kick users that try to send illegal packets
        logger.warning("Illegal packet from " + session.bedrockUsername() + ": " + rootCause.getMessage());
        if (logger.isDebug()) {
            cause.printStackTrace();
        }
        session.disconnect("Invalid packet received!");
    }
}
