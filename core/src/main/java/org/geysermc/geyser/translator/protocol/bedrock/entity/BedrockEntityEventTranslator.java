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

package org.geysermc.geyser.translator.protocol.bedrock.entity;

import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.MerchantContainer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSelectTradePacket;

import java.util.concurrent.TimeUnit;

@Translator(packet = EntityEventPacket.class)
public class BedrockEntityEventTranslator extends PacketTranslator<EntityEventPacket> {

    @Override
    public void translate(GeyserSession session, EntityEventPacket packet) {
        switch (packet.getType()) {
            case EATING_ITEM -> {
                // Resend the packet so we get the eating sounds
                session.sendUpstreamPacket(packet);
                return;
            }
            case COMPLETE_TRADE -> {
                // Not sent as of 1.18.10
                ServerboundSelectTradePacket selectTradePacket = new ServerboundSelectTradePacket(packet.getData());
                session.sendDownstreamGamePacket(selectTradePacket);

                session.scheduleInEventLoop(() -> {
                    Inventory openInventory = session.getOpenInventory();
                    if (openInventory instanceof MerchantContainer merchantInventory) {
                        merchantInventory.onTradeSelected(session, packet.getData());
                    }
                }, 100, TimeUnit.MILLISECONDS);
                return;
            }
        }
        GeyserLogger.get().debug("Did not translate incoming EntityEventPacket: " + packet);
    }
}
