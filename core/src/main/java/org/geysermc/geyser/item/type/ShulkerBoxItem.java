/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.geysermc.geyser.inventory.item.Potion;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.translator.item.BedrockItemBuilder;
import org.geysermc.geyser.translator.item.CustomItemTranslator;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.PotionContents;

import java.util.ArrayList;
import java.util.List;

public class ShulkerBoxItem extends BlockItem {
    public ShulkerBoxItem(Builder builder, Block block, Block... otherBlocks) {
        super(builder, block, otherBlocks);
    }

    @Override
    public void translateComponentsToBedrock(@NonNull GeyserSession session, @NonNull DataComponents components, @NonNull BedrockItemBuilder builder) {
        super.translateComponentsToBedrock(session, components, builder);

        List<ItemStack> contents = components.get(DataComponentTypes.CONTAINER);
        if (contents == null || contents.isEmpty()) {
            // Empty shulker box
            return;
        }
        List<NbtMap> itemsList = new ArrayList<>();
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack item = contents.get(slot);
            if (item == null || item.getId() == Items.AIR_ID) {
                continue;
            }
            ItemMapping boxMapping = session.getItemMappings().getMapping(item.getId());

            int bedrockData = boxMapping.getBedrockData();
            String bedrockIdentifier = boxMapping.getBedrockIdentifier();
            DataComponents boxComponents = item.getDataComponentsPatch();

            if (boxComponents != null) {
                // Check for custom items
                ItemDefinition customItemDefinition = CustomItemTranslator.getCustomItem(session, item.getAmount(), boxComponents, boxMapping);
                if (customItemDefinition != null) {
                    bedrockIdentifier = customItemDefinition.getIdentifier();
                    bedrockData = 0;
                } else {
                    // Manual checks for potions/tipped arrows
                    if (boxMapping.getJavaItem() instanceof PotionItem || boxMapping.getJavaItem() instanceof ArrowItem) {
                        PotionContents potionContents = boxComponents.get(DataComponentTypes.POTION_CONTENTS);
                        if (potionContents != null) {
                            Potion potion = Potion.getByJavaId(potionContents.getPotionId());
                            if (potion != null) {
                                bedrockData = potion.getBedrockId();
                            }
                        }
                    }
                }
            }

            NbtMapBuilder boxItemNbt = BedrockItemBuilder.createItemNbt(bedrockIdentifier, item.getAmount(), bedrockData); // Final item tag to add to the list
            boxItemNbt.putByte("Slot", (byte) slot);
            boxItemNbt.putByte("WasPickedUp", (byte) 0); // ??? TODO might not be needed

            // Only the display name is what we have interest in, so just translate that if relevant
            if (boxComponents != null) {
                String customName = ItemTranslator.getCustomName(session, boxComponents, boxMapping, '7', false, true);
                if (customName != null) {
                    // Fix count display (e.g., x16) with incorrect color due to some items with colored names
                    if (customName.contains("" + ChatColor.ESCAPE)) {
                        customName += ChatColor.RESET + ChatColor.GRAY;
                    }
                    boxItemNbt.putCompound("tag", NbtMap.builder()
                            .putCompound("display", NbtMap.builder()
                                    .putString("Name", customName)
                                    .build())
                            .build());
                }
            }

            itemsList.add(boxItemNbt.build());
        }
        builder.putList("Items", NbtType.COMPOUND, itemsList);
    }
}
