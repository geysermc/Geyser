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

package org.geysermc.geyser.translator.protocol.java.level;

import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCooldownPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerStartItemCooldownPacket;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

@Translator(packet = ClientboundCooldownPacket.class)
public class JavaCooldownTranslator extends PacketTranslator<ClientboundCooldownPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundCooldownPacket packet) {
        // If the cooldown group is a modded item, an item that Bedrock doesn't support custom cooldowns for, or a custom cooldown group,
        // then the cooldown won't be translated correctly. The cooldown won't show up on Bedrock, but they are still unable to use the item.
        Key cooldownGroup = packet.getCooldownGroup();
        Item item = Registries.JAVA_ITEM_IDENTIFIERS.get(cooldownGroup.asString());

        // Custom items can define an item cooldown using a custom cooldown group, which will be sent to the client if there's not a vanilla cooldown group
        String cooldownCategory = cooldownGroup.asString();
        // Not every vanilla item, as of 1.19, appears to be server-driven. Just these two.
        // Use a map here if it gets too big.
        if (item == Items.GOAT_HORN) {
            cooldownCategory = "goat_horn";
        } else if (item == Items.SHIELD) {
            cooldownCategory = "shield";
        }

        PlayerStartItemCooldownPacket bedrockPacket = new PlayerStartItemCooldownPacket();
        bedrockPacket.setItemCategory(cooldownCategory);
        bedrockPacket.setCooldownDuration(Math.round(packet.getCooldownTicks() * (session.getMillisecondsPerTick() / 50)));
        session.sendUpstreamPacket(bedrockPacket);

        session.getWorldCache().setCooldown(cooldownGroup, packet.getCooldownTicks());
    }
}
