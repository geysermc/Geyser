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

import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.LecternContainer;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.LecternInventoryTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerButtonClickPacket;

/**
 * Used to translate moving pages, or closing the inventory
 */
@Translator(packet = LecternUpdatePacket.class)
public class BedrockLecternUpdateTranslator extends PacketTranslator<LecternUpdatePacket> {

    @Override
    public void translate(GeyserSession session, LecternUpdatePacket packet) {
        // Bedrock wants to either move a page or exit
        if (!(session.getOpenInventory() instanceof LecternContainer lecternContainer)) {
            GeyserLogger.get().debug("Expected lectern but it wasn't open!");
            return;
        }

        if (lecternContainer.getCurrentBedrockPage() == packet.getPage()) {
            // The same page means Bedrock is closing the window
            InventoryUtils.sendJavaContainerClose(session, lecternContainer);
            InventoryUtils.closeInventory(session, lecternContainer.getJavaId(), false);
        } else {
            // Each "page" Bedrock gives to us actually represents two pages (think opening a book and seeing two pages)
            // Each "page" on Java is just one page (think a spiral notebook folded back to only show one page)
            int newJavaPage = (packet.getPage() * 2);
            int currentJavaPage = (lecternContainer.getCurrentBedrockPage() * 2);

            // So, fun fact: We need to separately handle fake lecterns!
            // Since those are not actually a real lectern... the Java server won't respond to our requests.
            if (!lecternContainer.isUsingRealBlock()) {
                LecternInventoryTranslator translator = (LecternInventoryTranslator) session.getInventoryTranslator();
                Inventory inventory = session.getOpenInventory();
                translator.updateProperty(session, inventory, 0, newJavaPage);
                return;
            }

            // Send as many click button packets as we need to
            // Java has the option to specify exact page numbers by adding 100 to the number, but buttonId variable
            // is a byte when transmitted over the network and therefore this stops us at 128
            if (newJavaPage > currentJavaPage) {
                for (int i = currentJavaPage; i < newJavaPage; i++) {
                    ServerboundContainerButtonClickPacket clickButtonPacket = new ServerboundContainerButtonClickPacket(session.getOpenInventory().getJavaId(), 2);
                    session.sendDownstreamGamePacket(clickButtonPacket);
                }
            } else {
                for (int i = currentJavaPage; i > newJavaPage; i--) {
                    ServerboundContainerButtonClickPacket clickButtonPacket = new ServerboundContainerButtonClickPacket(session.getOpenInventory().getJavaId(), 1);
                    session.sendDownstreamGamePacket(clickButtonPacket);
                }
            }
        }
    }
}
