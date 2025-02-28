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

package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Used to send the forwarded keep alive packet back to the server
 */
@Translator(packet = NetworkStackLatencyPacket.class)
public class BedrockNetworkStackLatencyTranslator extends PacketTranslator<NetworkStackLatencyPacket> {

    @Override
    public void translate(GeyserSession session, NetworkStackLatencyPacket packet) {
        // negative timestamps are used as hack to fix the url image loading bug
        if (packet.getTimestamp() >= 0) {
            if (session.getGeyser().config().forwardPlayerPing()) {
                // use our cached value because
                // a) bedrock can be inaccurate with the value returned
                // b) playstation replies with a different magnitude than other platforms
                // c) 1.20.10 and later reply with a different magnitude
                Long keepAliveId = session.getKeepAliveCache().poll();
                if (keepAliveId == null) {
                    session.getGeyser().getLogger().debug("Received a latency packet that we don't have a KeepAlive for: " + packet);
                    return;
                }

                ServerboundKeepAlivePacket keepAlivePacket = new ServerboundKeepAlivePacket(keepAliveId);
                session.sendDownstreamPacket(keepAlivePacket);
            }
            return;
        }

        session.scheduleInEventLoop(() -> {
            // Hack to fix the url image loading bug
            UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
            attributesPacket.setRuntimeEntityId(session.getPlayerEntity().getGeyserId());

            AttributeData attribute = session.getPlayerEntity().getAttributes().get(GeyserAttributeType.EXPERIENCE_LEVEL);
            if (attribute != null) {
                attributesPacket.setAttributes(Collections.singletonList(attribute));
            } else {
                attributesPacket.setAttributes(Collections.singletonList(GeyserAttributeType.EXPERIENCE_LEVEL.getAttribute(0)));
            }

            session.sendUpstreamPacket(attributesPacket);
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean shouldExecuteInEventLoop() {
        return false;
    }
}
