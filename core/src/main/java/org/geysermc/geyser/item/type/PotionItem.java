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

package org.geysermc.geyser.item.type;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.item.Potion;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.CustomItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.PotionContents;

public class PotionItem extends Item {
    public PotionItem(String javaIdentifier, Builder builder) {
        super(javaIdentifier, builder);
    }

    @Override
    public ItemData.Builder translateToBedrock(GeyserSession session, int count, DataComponents components, ItemMapping mapping, ItemMappings mappings) {
        if (components == null) return super.translateToBedrock(session, count, components, mapping, mappings);
        PotionContents potionContents = components.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents != null) {
            ItemDefinition customItemDefinition = CustomItemTranslator.getCustomItem(components, mapping);
            if (customItemDefinition == null) {
                Potion potion = Potion.getByJavaId(potionContents.getPotionId());
                if (potion != null) {
                    return ItemData.builder()
                            .definition(mapping.getBedrockDefinition())
                            .damage(potion.getBedrockId())
                            .count(count);
                }
                GeyserLogger.get().debug("Unknown Java potion: " + potionContents.getPotionId());
            } else {
                return ItemData.builder()
                        .definition(customItemDefinition)
                        .count(count);
            }
        }
        return super.translateToBedrock(session, count, components, mapping, mappings);
    }

    @Override
    public @NonNull GeyserItemStack translateToJava(GeyserSession session, @NonNull ItemData itemData, @NonNull ItemMapping mapping, @NonNull ItemMappings mappings) {
        Potion potion = Potion.getByBedrockId(itemData.getDamage());
        GeyserItemStack itemStack = super.translateToJava(session, itemData, mapping, mappings);
        if (potion != null) {
            itemStack.getOrCreateComponents().put(DataComponentTypes.POTION_CONTENTS, potion.toComponent());
        }
        return itemStack;
    }

    @Override
    public boolean ignoreDamage() {
        return true;
    }
}
