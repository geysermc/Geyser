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

package org.geysermc.geyser.registry.type;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import lombok.Builder;
import lombok.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.inventory.item.StoredItemMappings;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Builder
@Value
public class ItemMappings implements DefinitionRegistry<ItemDefinition> {

    Map<String, ItemMapping> cachedJavaMappings = new WeakHashMap<>();

    ItemMapping[] items;

    /**
     * A unique exception as this is an item in Bedrock, but not in Java.
     */
    ItemMapping lodestoneCompass;
    Int2ObjectMap<ItemMapping> lightBlocks;

    List<CreativeItemGroup> creativeItemGroups;
    List<CreativeItemData> creativeItems;
    Int2ObjectMap<ItemDefinition> itemDefinitions;

    StoredItemMappings storedItems;
    Set<Item> javaOnlyItems;

    List<ItemDefinition> buckets;
    List<ItemDefinition> boats;

    List<ItemDefinition> componentItemData; // TODO get rid of?
    Int2ObjectMap<String> customIdMappings;

    Object2ObjectMap<CustomBlockData, ItemDefinition> customBlockItemDefinitions;

    /**
     * Gets an {@link ItemMapping} from the given {@link ItemStack}.
     *
     * @param itemStack the itemstack
     * @return an item entry from the given java edition identifier
     */
    @NonNull
    public ItemMapping getMapping(@NonNull ItemStack itemStack) {
        return this.getMapping(itemStack.getId());
    }

    /**
     * Gets an {@link ItemMapping} from the given Minecraft: Java
     * Edition id.
     *
     * @param javaId the id
     * @return an item entry from the given java edition identifier
     */
    @NonNull
    public ItemMapping getMapping(int javaId) {
        return javaId >= 0 && javaId < this.items.length ? this.items[javaId] : ItemMapping.AIR;
    }

    @NonNull
    public ItemMapping getMapping(Item javaItem) {
        return getMapping(javaItem.javaId());
    }

    /**
     * Gets an {@link ItemMapping} from the given Minecraft: Java Edition
     * block state identifier.
     *
     * @param javaIdentifier the block state identifier
     * @return an item entry from the given java edition identifier
     */
    @Nullable
    public ItemMapping getMapping(String javaIdentifier) {
        return this.cachedJavaMappings.computeIfAbsent(javaIdentifier, key -> {
            for (ItemMapping mapping : this.items) {
                if (mapping.getJavaItem().javaIdentifier().equals(key)) {
                    return mapping;
                }
            }
            return null;
        });
    }

    /**
     * Gets an {@link ItemMapping} from the given {@link ItemData}.
     *
     * @param data the item data
     * @return an item entry from the given item data
     */
    @NonNull
    public ItemMapping getMapping(ItemData data) {
        ItemDefinition definition = data.getDefinition();
        if (ItemDefinition.AIR.equals(definition)) {
            return ItemMapping.AIR;
        } else if (definition.getRuntimeId() == lodestoneCompass.getBedrockDefinition().getRuntimeId()) {
            return lodestoneCompass;
        }

        ItemMapping lightBlock = lightBlocks.get(definition.getRuntimeId());
        if (lightBlock != null) {
            return lightBlock;
        }

        boolean isBlock = data.getBlockDefinition() != null && data.getBlockDefinition().getRuntimeId() != 0;
        boolean hasDamage = data.getDamage() != 0;

        for (ItemMapping mapping : this.items) {
            if (mapping.getBedrockDefinition().getRuntimeId() == definition.getRuntimeId()) {
                if (isBlock && !hasDamage) { // Pre-1.16.220 will not use block runtime IDs at all, so we shouldn't check either
                    if (data.getBlockDefinition() != mapping.getBedrockBlockDefinition()) {
                        continue;
                    }
                } else {
                    if (!(mapping.getBedrockData() == data.getDamage() ||
                            // Make exceptions for items whose damage values can vary
                            (mapping.getJavaItem().ignoreDamage() || mapping.getJavaItem() == Items.SUSPICIOUS_STEW))) {
                        continue;
                    }
                }
                if (!this.javaOnlyItems.contains(mapping.getJavaItem())) {
                    // From a Bedrock item data, we aren't getting one of these items
                    return mapping;
                }
            }
        }

        GeyserImpl.getInstance().getLogger().debug("Missing mapping for bedrock item " + data);
        return ItemMapping.AIR;
    }

    @Nullable
    @Override
    public ItemDefinition getDefinition(int bedrockId) {
        return this.itemDefinitions.get(bedrockId);
    }

    @Nullable
    public ItemDefinition getDefinition(String bedrockIdentifier) {
        for (ItemDefinition itemDefinition : this.itemDefinitions.values()) {
            if (itemDefinition.getIdentifier().equals(bedrockIdentifier)) {
                return itemDefinition;
            }
        }
        return null;
    }

    @Override
    public boolean isRegistered(ItemDefinition definition) {
        return getDefinition(definition.getRuntimeId()) == definition;
    }
}
