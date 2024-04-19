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

package org.geysermc.geyser.entity.type.living.animal.horse;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

public class LlamaEntity extends ChestedHorseEntity {

    public LlamaEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);

        dirtyMetadata.put(EntityDataTypes.CONTAINER_STRENGTH_MODIFIER, 3); // Presumably 3 slots for every 1 strength
    }

    /**
     * Color equipped on the llama
     */
    public void setCarpetedColor(IntEntityMetadata entityMetadata) {
        // Bedrock treats llama decoration as armor
        MobArmorEquipmentPacket equipmentPacket = new MobArmorEquipmentPacket();
        equipmentPacket.setRuntimeEntityId(geyserId);
        // -1 means no armor
        int carpetIndex = entityMetadata.getPrimitiveValue();
        if (carpetIndex > -1 && carpetIndex <= 15) {
            // The damage value is the dye color that Java sends us, for pre-1.16.220
            // The item is always going to be a carpet
            equipmentPacket.setChestplate(session.getItemMappings().getCarpets().get(carpetIndex));
        } else {
            equipmentPacket.setChestplate(ItemData.AIR);
        }
        // Required to fill out the rest of the equipment or Bedrock ignores it, including above else statement if removing armor
        equipmentPacket.setBoots(ItemData.AIR);
        equipmentPacket.setHelmet(ItemData.AIR);
        equipmentPacket.setLeggings(ItemData.AIR);

        session.sendUpstreamPacket(equipmentPacket);
    }

    @Override
    public boolean canEat(Item item) {
        return item == Items.WHEAT || item == Items.HAY_BLOCK;
    }
}
