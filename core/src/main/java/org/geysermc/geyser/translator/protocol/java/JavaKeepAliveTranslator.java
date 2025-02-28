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

package org.geysermc.geyser.translator.protocol.java;

import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

/**
 * Used to forward the keep alive packet to the client in order to get back a reliable ping.
 */
@Translator(packet = ClientboundKeepAlivePacket.class)
public class JavaKeepAliveTranslator extends PacketTranslator<ClientboundKeepAlivePacket> {

    @Override
    public void translate(GeyserSession session, ClientboundKeepAlivePacket packet) {
        if (!session.getGeyser().config().forwardPlayerPing()) {
            return;
        }
        // We use this once the client replies (see BedrockNetworkStackLatencyTranslator)
        session.getKeepAliveCache().add(packet.getPingId());

        long timestamp = packet.getPingId();

        // We take the abs because we rely on the client responding with a negative value ONLY when we send
        // a negative timestamp in the form-image-hack performed in FormCache.
        // Apart from that case, we don't actually use the value the client responds with, instead using our keep alive cache.
        if (timestamp == Long.MIN_VALUE) {
            timestamp = Long.MAX_VALUE; // There is not an abs representation of MIN_VALUE (2's complement)
        } else {
            timestamp = Math.absExact(timestamp);
        }

        // Bedrock will overflow on timestamps that are too large, and respond with a mangled negative value.
        // Keeping leftmost digits allows for easier debugging
        while (timestamp > 1e10) {
            timestamp /= 10;
        }

        NetworkStackLatencyPacket latencyPacket = new NetworkStackLatencyPacket();
        latencyPacket.setFromServer(true);
        latencyPacket.setTimestamp(timestamp);
        session.sendUpstreamPacketImmediately(latencyPacket);
    }
}
