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

package org.geysermc.geyser.registry.populator;

import com.fasterxml.jackson.databind.JsonNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.geysermc.geyser.registry.type.GeyserMappingItem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class CreativeItemRegistryPopulator {
    private static final List<BiPredicate<String, Integer>> JAVA_ONLY_ITEM_FILTER = List.of(
            // Bedrock-only as its own item
            (identifier, data) -> identifier.equals("minecraft:empty_map") && data == 2
    );

    static List<CreativeItemGroup> readCreativeItemGroups(ItemRegistryPopulator.PaletteVersion palette, List<CreativeItemData> creativeItemData) {
        GeyserBootstrap bootstrap = GeyserImpl.getInstance().getBootstrap();

        JsonNode creativeItemEntries;
        try (InputStream stream = bootstrap.getResourceOrThrow(String.format("bedrock/creative_items.%s.json", palette.version()))) {
            creativeItemEntries = GeyserImpl.JSON_MAPPER.readTree(stream).get("groups");
        } catch (Exception e) {
            throw new AssertionError("Unable to load creative item groups", e);
        }

        List<CreativeItemGroup> creativeItemGroups = new ArrayList<>();
        for (JsonNode creativeItemEntry : creativeItemEntries) {
            CreativeItemCategory category = CreativeItemCategory.valueOf(creativeItemEntry.get("category").asText().toUpperCase(Locale.ROOT));
            String name = creativeItemEntry.get("name").asText();

            JsonNode icon = creativeItemEntry.get("icon");
            String identifier = icon.get("id").asText();

            ItemData itemData;
            if (identifier.equals("minecraft:air")) {
                itemData = ItemData.AIR;
            } else {
                itemData = creativeItemData.stream()
                    .map(CreativeItemData::getItem)
                    .filter(item -> item.getDefinition().getIdentifier().equals(identifier))
                    .findFirst()
                    .orElseThrow();
            }

            creativeItemGroups.add(new CreativeItemGroup(category, name, itemData));
        }

        return creativeItemGroups;
    }

    static void populate(ItemRegistryPopulator.PaletteVersion palette, Map<String, ItemDefinition> definitions, Map<String, GeyserMappingItem> items, BiConsumer<ItemData.Builder, Integer> itemConsumer) {
        GeyserBootstrap bootstrap = GeyserImpl.getInstance().getBootstrap();

        // Load creative items
        JsonNode creativeItemEntries;
        try (InputStream stream = bootstrap.getResourceOrThrow(String.format("bedrock/creative_items.%s.json", palette.version()))) {
            creativeItemEntries = GeyserImpl.JSON_MAPPER.readTree(stream).get("items");
        } catch (Exception e) {
            throw new AssertionError("Unable to load creative items", e);
        }

        BlockMappings blockMappings = BlockRegistries.BLOCKS.forVersion(palette.protocolVersion());
        for (JsonNode itemNode : creativeItemEntries) {
            ItemData.Builder itemBuilder = createItemData(itemNode, items, blockMappings, definitions);
            if (itemBuilder == null) {
                continue;
            }

            int groupId = itemNode.get("groupId") != null ? itemNode.get("groupId").asInt() : 0;

            itemConsumer.accept(itemBuilder, groupId);
        }
    }

    private static ItemData.@Nullable Builder createItemData(JsonNode itemNode, Map<String, GeyserMappingItem> items, BlockMappings blockMappings, Map<String, ItemDefinition> definitions) {
        int count = 1;
        int damage = 0;
        NbtMap tag = null;

        String identifier = itemNode.get("id").textValue();
        for (BiPredicate<String, Integer> predicate : JAVA_ONLY_ITEM_FILTER) {
            if (predicate.test(identifier, damage)) {
                return null;
            }
        }

        // Attempt to remove items that do not exist in Java (1.21.50 has 1.21.4 items, that don't exist on 1.21.2)
        // we still add the lodestone compass - we're going to translate it.
        if (!items.containsKey(identifier) && !identifier.equals("minecraft:lodestone_compass")) {
            // bedrock identifier not found, let's make sure it's not just different
            boolean found = false;
            for (var mapping : items.values()) {
                if (mapping.getBedrockIdentifier().equals(identifier)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return null;
            }
        }

        JsonNode damageNode = itemNode.get("damage");
        if (damageNode != null) {
            damage = damageNode.asInt();
        }

        JsonNode countNode = itemNode.get("count");
        if (countNode != null) {
            count = countNode.asInt();
        }

        GeyserBedrockBlock blockDefinition = null;
        JsonNode blockStateNode;
        if ((blockStateNode = itemNode.get("block_state_b64")) != null) {
            byte[] bytes = Base64.getDecoder().decode(blockStateNode.asText());
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try {
                NbtMap stateTag = (NbtMap) NbtUtils.createReaderLE(bais).readTag();

                // We remove these from the state definition map in
                // BlockMappings, so we need to remove it from here
                NbtMapBuilder builder = stateTag.toBuilder();
                builder.remove("name_hash");
                builder.remove("network_id");
                builder.remove("version");
                builder.remove("block_id");

                blockDefinition = blockMappings.getDefinition(builder.build());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JsonNode nbtNode = itemNode.get("nbt_b64");
        if (nbtNode != null) {
            byte[] bytes = Base64.getDecoder().decode(nbtNode.asText());
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try {
                tag = (NbtMap) NbtUtils.createReaderLE(bais).readTag();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ItemDefinition definition = definitions.get(identifier);
        if (definition == null) {
            GeyserLogger.get().debug("Unknown item definition with identifier " + identifier + " when loading creative items!");
            return null;
        }

        return ItemData.builder()
                .definition(definition)
                .damage(damage)
                .count(count)
                .tag(tag)
                .blockDefinition(blockDefinition);
    }
}
