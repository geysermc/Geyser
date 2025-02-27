/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.protocol.java.inventory;

import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

@Translator(packet = ClientboundSetPlayerInventoryPacket.class)
public class JavaSetPlayerInventoryTranslator extends PacketTranslator<ClientboundSetPlayerInventoryPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundSetPlayerInventoryPacket packet) {
        int slot = packet.getSlot();
        if (slot >= 0 && slot <= 8) {
            // As of 1.21.3 - can be replicated in vanilla server survival by picking an item in-world in your inventory not in your hotbar.
            slot = session.getPlayerInventory().getOffsetForHotbar(slot);
        }

        if (slot >= session.getPlayerInventory().getSize()) {
            GeyserLogger logger = GeyserLogger.get();
            logger.warning("ClientboundSetPlayerInventoryPacket sent to " + session.bedrockUsername()
                + " that exceeds inventory size!");
            if (logger.isDebug()) {
                logger.debug(packet.toString());
                logger.debug(session.getPlayerInventory().toString());
            }
            return;
        }

        GeyserItemStack newItem = GeyserItemStack.from(packet.getContents());
        session.getBundleCache().initialize(newItem);
        session.getPlayerInventory().setItem(slot, newItem, session);
        InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, session.getPlayerInventory(), slot);
    }
}
