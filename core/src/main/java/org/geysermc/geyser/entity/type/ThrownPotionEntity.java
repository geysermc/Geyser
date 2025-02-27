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

package org.geysermc.geyser.entity.type;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.inventory.item.Potion;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.PotionContents;

import java.util.EnumSet;
import java.util.UUID;

public class ThrownPotionEntity extends ThrowableItemEntity {
    private static final EnumSet<Potion> NON_ENCHANTED_POTIONS = EnumSet.of(Potion.WATER, Potion.MUNDANE, Potion.THICK, Potion.AWKWARD);

    public ThrownPotionEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, EntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    public void setItem(EntityMetadata<ItemStack, ?> entityMetadata) {
        ItemStack itemStack = entityMetadata.getValue();
        if (itemStack == null) {
            dirtyMetadata.put(EntityDataTypes.AUX_VALUE_DATA, (short) 0);
            setFlag(EntityFlag.ENCHANTED, false);
            setFlag(EntityFlag.LINGERING, false);
        } else {
            // As of Java 1.19.3, the server/client doesn't seem to care of the item is actually a potion?
            DataComponents components = itemStack.getDataComponentsPatch();
            if (components != null) {
                PotionContents potionContents = components.get(DataComponentTypes.POTION_CONTENTS);
                if (potionContents != null) {
                    Potion potion = Potion.getByJavaId(potionContents.getPotionId());
                    if (potion != null) {
                        dirtyMetadata.put(EntityDataTypes.AUX_VALUE_DATA, potion.getBedrockId());
                        setFlag(EntityFlag.ENCHANTED, !NON_ENCHANTED_POTIONS.contains(potion));
                    } else {
                        dirtyMetadata.put(EntityDataTypes.AUX_VALUE_DATA, (short) 0);
                        GeyserLogger.get().debug("Unknown java potion: " + potionContents.getPotionId());
                    }
                }

                boolean isLingering = Registries.JAVA_ITEMS.get().get(itemStack.getId()) == Items.LINGERING_POTION;
                setFlag(EntityFlag.LINGERING, isLingering);
            }
        }
    }
}
