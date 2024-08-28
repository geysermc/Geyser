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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.*;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ComponentItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.inventory.item.StoredItemMappings;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.components.Rarity;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Populates the item registries.
 */
public class ItemRegistryPopulator {

    record PaletteVersion(String version, int protocolVersion, Map<Item, String> javaOnlyItems, Remapper remapper) {

        public PaletteVersion(String version, int protocolVersion) {
            this(version, protocolVersion, Collections.emptyMap(), (item, mapping) -> mapping);
        }
    }

    @FunctionalInterface
    interface Remapper {
        @NonNull
        GeyserMappingItem remap(Item item, GeyserMappingItem mapping);
    }

    public static void populate() {
        List<PaletteVersion> paletteVersions = new ArrayList<>(3);
        paletteVersions.add(new PaletteVersion("1_20_80", Bedrock_v671.CODEC.getProtocolVersion(), Collections.emptyMap(), Conversion685_671::remapItem));
        paletteVersions.add(new PaletteVersion("1_21_0", Bedrock_v685.CODEC.getProtocolVersion(), Collections.emptyMap(), Conversion712_685::remapItem));
        paletteVersions.add(new PaletteVersion("1_21_20", Bedrock_v712.CODEC.getProtocolVersion()));

        GeyserBootstrap bootstrap = GeyserImpl.getInstance().getBootstrap();

        TypeReference<Map<String, GeyserMappingItem>> mappingItemsType = new TypeReference<>() { };

        Map<String, GeyserMappingItem> items;
        try (InputStream stream = bootstrap.getResourceOrThrow("mappings/items.json")) {
            // Load item mappings from Java Edition to Bedrock Edition
            items = GeyserImpl.JSON_MAPPER.readValue(stream, mappingItemsType);
        } catch (Exception e) {
            throw new AssertionError("Unable to load Java runtime item IDs", e);
        }

        NbtMap vanillaComponents;
        try (InputStream stream = bootstrap.getResourceOrThrow("mappings/item_components.nbt")) {
            vanillaComponents = (NbtMap) NbtUtils.createGZIPReader(stream, true, true).readTag();
        } catch (Exception e) {
            throw new AssertionError("Unable to load Bedrock item components", e);
        }

        boolean customItemsAllowed = GeyserImpl.getInstance().getConfig().isAddNonBedrockItems();

        // List values here is important compared to HashSet - we need to preserve the order of what's given to us
        // (as of 1.19.2 Java) to replicate some edge cases in Java predicate behavior where it checks from the bottom
        // of the list first, then ascends.
        Multimap<String, CustomItemData> customItems = MultimapBuilder.hashKeys().arrayListValues().build();
        List<NonVanillaCustomItemData> nonVanillaCustomItems = customItemsAllowed ? new ObjectArrayList<>() : Collections.emptyList();

        if (customItemsAllowed) {
            CustomItemRegistryPopulator.populate(items, customItems, nonVanillaCustomItems);
        }

        // We can reduce some operations as Java information is the same across all palette versions
        boolean firstMappingsPass = true;

        /* Load item palette */
        for (PaletteVersion palette : paletteVersions) {
            TypeReference<List<PaletteItem>> paletteEntriesType = new TypeReference<>() {};

            List<PaletteItem> itemEntries;
            try (InputStream stream = bootstrap.getResourceOrThrow(String.format("bedrock/runtime_item_states.%s.json", palette.version()))) {
                itemEntries = GeyserImpl.JSON_MAPPER.readValue(stream, paletteEntriesType);
            } catch (Exception e) {
                throw new AssertionError("Unable to load Bedrock runtime item IDs", e);
            }

            // Used for custom items
            int nextFreeBedrockId = 0;
            List<ComponentItemData> componentItemData = new ObjectArrayList<>();

            Int2ObjectMap<ItemDefinition> registry = new Int2ObjectOpenHashMap<>();
            Map<String, ItemDefinition> definitions = new Object2ObjectLinkedOpenHashMap<>();

            for (PaletteItem entry : itemEntries) {
                int id = entry.getId();
                if (id >= nextFreeBedrockId) {
                    nextFreeBedrockId = id + 1;
                }

                ItemDefinition definition = new SimpleItemDefinition(entry.getName().intern(), id, false);
                definitions.put(entry.getName(), definition);
                registry.put(definition.getRuntimeId(), definition);
            }

            Object2ObjectMap<String, BlockDefinition> bedrockBlockIdOverrides = new Object2ObjectOpenHashMap<>();
            Object2IntMap<String> blacklistedIdentifiers = new Object2IntOpenHashMap<>();

            Object2ObjectMap<CustomBlockData, ItemDefinition> customBlockItemDefinitions = new Object2ObjectOpenHashMap<>();

            List<ItemDefinition> buckets = new ObjectArrayList<>();

            List<ItemMapping> mappings = new ObjectArrayList<>();
            // Temporary mapping to create stored items
            Map<Item, ItemMapping> javaItemToMapping = new Object2ObjectOpenHashMap<>();

            List<ItemData> creativeItems = new ArrayList<>();
            Set<String> noBlockDefinitions = new ObjectOpenHashSet<>();

            // Fix: Usage of structure blocks/voids in recipes
            // https://github.com/GeyserMC/Geyser/issues/2890
            noBlockDefinitions.add("minecraft:structure_block");
            noBlockDefinitions.add("minecraft:structure_void");

            AtomicInteger creativeNetId = new AtomicInteger();
            CreativeItemRegistryPopulator.populate(palette, definitions, itemBuilder -> {
                ItemData item = itemBuilder.netId(creativeNetId.incrementAndGet()).build();
                creativeItems.add(item);

                if (item.getBlockDefinition() != null) {
                    String identifier = item.getDefinition().getIdentifier();

                    // Add override for item mapping, unless it already exists... then we know multiple states can exist
                    if (!blacklistedIdentifiers.containsKey(identifier)) {
                        if (bedrockBlockIdOverrides.containsKey(identifier)) {
                            bedrockBlockIdOverrides.remove(identifier);
                            // Save this as a blacklist, but also as knowledge of what the block state name should be
                            blacklistedIdentifiers.put(identifier, item.getBlockDefinition().getRuntimeId());
                        } else {
                            // Unless there's multiple possibilities for this one state, let this be
                            bedrockBlockIdOverrides.put(identifier, item.getBlockDefinition());
                        }
                    }
                } else {
                    // Item mappings should also NOT have a block definition for these.
                    noBlockDefinitions.add(item.getDefinition().getIdentifier());
                }
            });

            BlockMappings blockMappings = BlockRegistries.BLOCKS.forVersion(palette.protocolVersion());

            Set<Item> javaOnlyItems = new ObjectOpenHashSet<>();
            Collections.addAll(javaOnlyItems, Items.SPECTRAL_ARROW, Items.DEBUG_STICK,
                    Items.KNOWLEDGE_BOOK, Items.TIPPED_ARROW, Items.BUNDLE);
            if (!customItemsAllowed) {
                javaOnlyItems.add(Items.FURNACE_MINECART);
            }
            // Java-only items for this version
            javaOnlyItems.addAll(palette.javaOnlyItems().keySet());

            Int2ObjectMap<String> customIdMappings = new Int2ObjectOpenHashMap<>();
            Set<String> registeredItemNames = new ObjectOpenHashSet<>(); // This is used to check for duplicate item names

            for (Map.Entry<String, GeyserMappingItem> entry : items.entrySet()) {
                Item javaItem = Registries.JAVA_ITEM_IDENTIFIERS.get(entry.getKey());
                if (javaItem == null) {
                    throw new RuntimeException("Extra item in mappings? " + entry.getKey());
                }
                GeyserMappingItem mappingItem;
                String replacementItem = palette.javaOnlyItems().get(javaItem);
                if (replacementItem != null) {
                    mappingItem = items.get(replacementItem); // java only item, a java id fallback has been provided
                } else {
                    // check if any mapping changes need to be made on this version
                    mappingItem = palette.remapper().remap(javaItem, entry.getValue());
                }

                if (customItemsAllowed && javaItem == Items.FURNACE_MINECART) {
                    // Will be added later
                    mappings.add(null);
                    continue;
                }

                String bedrockIdentifier = mappingItem.getBedrockIdentifier();
                ItemDefinition definition = definitions.get(bedrockIdentifier);
                if (definition == null) {
                    throw new RuntimeException("Missing Bedrock ItemDefinition in version " + palette.version() + " for mapping: " + mappingItem);
                }

                BlockDefinition bedrockBlock = null;
                Integer firstBlockRuntimeId = entry.getValue().getFirstBlockRuntimeId();
                BlockDefinition customBlockItemOverride = null;
                if (firstBlockRuntimeId != null) {
                    BlockDefinition blockOverride = bedrockBlockIdOverrides.get(bedrockIdentifier);

                    // We'll do this here for custom blocks we want in the creative inventory so we can piggyback off the existing logic to find these
                    // blocks in creativeItems
                    CustomBlockData customBlockData = BlockRegistries.CUSTOM_BLOCK_ITEM_OVERRIDES.getOrDefault(javaItem.javaIdentifier(), null);
                    if (customBlockData != null) {
                        // this block has a custom item override and thus we should use its runtime ID for the ItemMapping
                        if (customBlockData.includedInCreativeInventory()) {
                            CustomBlockState customBlockState = customBlockData.defaultBlockState();
                            customBlockItemOverride = blockMappings.getCustomBlockStateDefinitions().getOrDefault(customBlockState, null);
                        }
                    }

                    // If it' s a custom block we can't do this because we need to make sure we find the creative item
                    if (blockOverride != null && customBlockItemOverride == null) {
                        // Straight from BDS is our best chance of getting an item that doesn't run into issues
                        bedrockBlock = blockOverride;
                    } else {
                        // Try to get an example block runtime ID from the creative contents packet, for Bedrock identifier obtaining
                        int aValidBedrockBlockId = blacklistedIdentifiers.getOrDefault(bedrockIdentifier, customBlockItemOverride != null ? customBlockItemOverride.getRuntimeId() : -1);
                        if (aValidBedrockBlockId == -1 && customBlockItemOverride == null) {
                            // Fallback
                            if (!noBlockDefinitions.contains(entry.getValue().getBedrockIdentifier())) {
                                bedrockBlock = blockMappings.getBedrockBlock(firstBlockRuntimeId);
                            }
                        } else {
                            // As of 1.16.220, every item requires a block runtime ID attached to it.
                            // This is mostly for identifying different blocks with the same item ID - wool, slabs, some walls.
                            // However, in order for some visuals and crafting to work, we need to send the first matching block state
                            // as indexed by Bedrock's block palette
                            // There are exceptions! But, ideally, the block ID override should take care of those.
                            NbtMapBuilder requiredBlockStatesBuilder = NbtMap.builder();
                            String correctBedrockIdentifier = blockMappings.getDefinition(aValidBedrockBlockId).getState().getString("name");
                            boolean firstPass = true;
                            // Block states are all grouped together. In the mappings, we store the first block runtime ID in order,
                            // and the last, if relevant. We then iterate over all those values and get their Bedrock equivalents
                            int lastBlockRuntimeId = entry.getValue().getLastBlockRuntimeId() == null ? firstBlockRuntimeId : entry.getValue().getLastBlockRuntimeId();
                            for (int i = firstBlockRuntimeId; i <= lastBlockRuntimeId; i++) {
                                GeyserBedrockBlock bedrockBlockRuntimeId = blockMappings.getVanillaBedrockBlock(i);
                                NbtMap blockTag = bedrockBlockRuntimeId.getState();
                                String bedrockName = blockTag.getString("name");
                                if (!bedrockName.equals(correctBedrockIdentifier)) {
                                    continue;
                                }
                                NbtMap states = blockTag.getCompound("states");

                                if (firstPass) {
                                    firstPass = false;
                                    if (states.isEmpty()) {
                                        // No need to iterate and find all block states - this is the one, as there can't be any others
                                        bedrockBlock = bedrockBlockRuntimeId;
                                        break;
                                    }
                                    requiredBlockStatesBuilder.putAll(states);
                                    continue;
                                }
                                for (Map.Entry<String, Object> nbtEntry : states.entrySet()) {
                                    Object value = requiredBlockStatesBuilder.get(nbtEntry.getKey());
                                    if (value != null && !nbtEntry.getValue().equals(value)) { // Null means this value has already been removed/deemed as unneeded
                                        // This state can change between different block states, and therefore is not required
                                        // to build a successful block state of this
                                        requiredBlockStatesBuilder.remove(nbtEntry.getKey());
                                    }
                                }
                                if (requiredBlockStatesBuilder.isEmpty()) {
                                    // There are no required block states
                                    // E.G. there was only a direction property that is no longer in play
                                    // (States that are important include color for glass)
                                    break;
                                }
                            }

                            NbtMap requiredBlockStates = requiredBlockStatesBuilder.build();
                            if (bedrockBlock == null) {
                                // We need to loop around again (we can't cache the block tags above) because Bedrock can include states that we don't have a pairing for
                                // in it's "preferred" block state - I.E. the first matching block state in the list
                                for (GeyserBedrockBlock block : blockMappings.getBedrockRuntimeMap()) {
                                    if (block == null) {
                                        continue;
                                    }
                                    NbtMap blockTag = block.getState();
                                    if (blockTag.getString("name").equals(correctBedrockIdentifier)) {
                                        NbtMap states = blockTag.getCompound("states");
                                        boolean valid = true;
                                        for (Map.Entry<String, Object> nbtEntry : requiredBlockStates.entrySet()) {
                                            if (!states.get(nbtEntry.getKey()).equals(nbtEntry.getValue())) {
                                                // A required block state doesn't match - this one is not valid
                                                valid = false;
                                                break;
                                            }
                                        }
                                        if (valid) {
                                            bedrockBlock = block;
                                            break;
                                        }
                                    }
                                }
                                if (bedrockBlock == null) {
                                    throw new RuntimeException("Could not find a block match for " + entry.getKey());
                                }
                            }

                            // Because we have replaced the Bedrock block ID, we also need to replace the creative contents block runtime ID
                            // That way, creative items work correctly for these blocks

                            // Set our custom block override now if there is one
                            if (customBlockItemOverride != null) {
                                bedrockBlock = customBlockItemOverride;
                            }

                            for (int j = 0; j < creativeItems.size(); j++) {
                                ItemData itemData = creativeItems.get(j);
                                if (itemData.getDefinition().equals(definition)) {
                                    if (itemData.getDamage() != 0) {
                                        break;
                                    }

                                    NbtMap states = ((GeyserBedrockBlock) itemData.getBlockDefinition()).getState().getCompound("states");

                                    boolean valid = true;
                                    for (Map.Entry<String, Object> nbtEntry : requiredBlockStates.entrySet()) {
                                        if (!Objects.equals(states.get(nbtEntry.getKey()), nbtEntry.getValue())) {
                                            // A required block state doesn't match - this one is not valid
                                            valid = false;
                                            break;
                                        }
                                    }
                                    if (valid) {
                                        if (customBlockItemOverride != null && customBlockData != null) {
                                            // Assuming this is a valid custom block override we'll just register it now while we have the creative item
                                            int customProtocolId = nextFreeBedrockId++;
                                            mappingItem = mappingItem.withBedrockData(customProtocolId);
                                            bedrockIdentifier = customBlockData.identifier();
                                            definition = new SimpleItemDefinition(bedrockIdentifier, customProtocolId, true);
                                            registry.put(customProtocolId, definition);
                                            customBlockItemDefinitions.put(customBlockData, definition);
                                            customIdMappings.put(customProtocolId, bedrockIdentifier);
                                            
                                            creativeItems.set(j, itemData.toBuilder()
                                                .definition(definition)
                                                .blockDefinition(bedrockBlock)
                                                .netId(itemData.getNetId())
                                                .count(1)
                                                .build());
                                        } else {
                                            creativeItems.set(j, itemData.toBuilder().blockDefinition(bedrockBlock).build());
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                ItemMapping.ItemMappingBuilder mappingBuilder = ItemMapping.builder()
                        .bedrockIdentifier(bedrockIdentifier.intern())
                        .bedrockDefinition(definition)
                        .bedrockData(mappingItem.getBedrockData())
                        .bedrockBlockDefinition(bedrockBlock)
                        .javaItem(javaItem);

                if (mappingItem.getToolType() != null) {
                    if (mappingItem.getToolTier() != null) {
                        mappingBuilder = mappingBuilder.toolType(mappingItem.getToolType().intern())
                                .toolTier(mappingItem.getToolTier().intern());
                    } else {
                        mappingBuilder = mappingBuilder.toolType(mappingItem.getToolType().intern())
                                .toolTier("");
                    }
                }

                if (javaOnlyItems.contains(javaItem) || javaItem.rarity() != Rarity.COMMON) {
                    // These items don't exist on Bedrock, so set up a variable that indicates they should have custom names
                    // Or, ensure that we are translating these at all times to account for rarity colouring
                    mappingBuilder = mappingBuilder.translationString((javaItem instanceof BlockItem ? "block." : "item.") + entry.getKey().replace(":", "."));
                    GeyserImpl.getInstance().getLogger().debug("Adding " + entry.getKey() + " as an item that needs to be translated.");
                }

                // Add the custom item properties, if applicable
                List<Pair<CustomItemOptions, ItemDefinition>> customItemOptions;
                Collection<CustomItemData> customItemsToLoad = customItems.get(javaItem.javaIdentifier());
                if (customItemsAllowed && !customItemsToLoad.isEmpty()) {
                    customItemOptions = new ObjectArrayList<>(customItemsToLoad.size());

                    for (CustomItemData customItem : customItemsToLoad) {
                        int customProtocolId = nextFreeBedrockId++;

                        String customItemName = customItem instanceof NonVanillaCustomItemData nonVanillaItem ? nonVanillaItem.identifier() : Constants.GEYSER_CUSTOM_NAMESPACE + ":" + customItem.name();
                        if (!registeredItemNames.add(customItemName)) {
                            if (firstMappingsPass) {
                                GeyserImpl.getInstance().getLogger().error("Custom item name '" + customItemName + "' already exists and was registered again! Skipping...");
                            }
                            continue;
                        }

                        GeyserCustomMappingData customMapping = CustomItemRegistryPopulator.registerCustomItem(
                                customItemName, javaItem, mappingItem, customItem, customProtocolId, palette.protocolVersion
                        );

                        if (customItem.creativeCategory().isPresent()) {
                            creativeItems.add(ItemData.builder()
                                    .netId(creativeNetId.incrementAndGet())
                                    .definition(customMapping.itemDefinition())
                                    .blockDefinition(null)
                                    .count(1)
                                    .build());
                        }

                        // ComponentItemData - used to register some custom properties
                        componentItemData.add(customMapping.componentItemData());
                        customItemOptions.add(Pair.of(customItem.customItemOptions(), customMapping.itemDefinition()));
                        registry.put(customMapping.integerId(), customMapping.itemDefinition());

                        customIdMappings.put(customMapping.integerId(), customMapping.stringId());
                    }

                    // Important for later to find the best match and accurately replicate Java behavior
                    Collections.reverse(customItemOptions);
                } else {
                    customItemOptions = Collections.emptyList();
                }
                mappingBuilder.customItemOptions(customItemOptions);

                ItemMapping mapping = mappingBuilder.build();

                if (javaItem.javaIdentifier().contains("bucket") && !javaItem.javaIdentifier().contains("milk")) {
                    buckets.add(definition);
                }

                mappings.add(mapping);
                javaItemToMapping.put(javaItem, mapping);
            }

            ItemDefinition lodestoneCompass = definitions.get("minecraft:lodestone_compass");
            if (lodestoneCompass == null) {
                throw new RuntimeException("Lodestone compass not found in item palette!");
            }

            // Add the lodestone compass since it doesn't exist on java but we need it for item conversion
            ItemMapping lodestoneEntry = ItemMapping.builder()
                    .javaItem(Items.COMPASS)
                    .bedrockIdentifier("minecraft:lodestone_compass")
                    .bedrockDefinition(lodestoneCompass)
                    .bedrockData(0)
                    .bedrockBlockDefinition(null)
                    .customItemOptions(Collections.emptyList())
                    .build();

            if (customItemsAllowed) {
                // Add furnace minecart
                ItemDefinition definition = new SimpleItemDefinition("geysermc:furnace_minecart", nextFreeBedrockId, true);
                definitions.put("geysermc:furnace_minecart", definition);
                registry.put(definition.getRuntimeId(), definition);

                mappings.set(Items.FURNACE_MINECART.javaId(), ItemMapping.builder()
                        .javaItem(Items.FURNACE_MINECART)
                        .bedrockIdentifier("geysermc:furnace_minecart")
                        .bedrockDefinition(definition)
                        .bedrockData(0)
                        .bedrockBlockDefinition(null)
                        .customItemOptions(Collections.emptyList()) // TODO check for custom items with furnace minecart
                        .build());

                creativeItems.add(ItemData.builder()
                        .netId(creativeNetId.incrementAndGet())
                        .definition(definition)
                        .count(1)
                        .build());

                registerFurnaceMinecart(nextFreeBedrockId++, componentItemData, palette.protocolVersion);

                // Register any completely custom items given to us
                IntSet registeredJavaIds = new IntOpenHashSet(); // Used to check for duplicate item java ids
                for (NonVanillaCustomItemData customItem : nonVanillaCustomItems) {
                    if (!registeredJavaIds.add(customItem.javaId())) {
                        if (firstMappingsPass) {
                            GeyserImpl.getInstance().getLogger().error("Custom item java id " + customItem.javaId() + " already exists and was registered again! Skipping...");
                        }
                        continue;
                    }

                    int customItemId = nextFreeBedrockId++;
                    NonVanillaItemRegistration registration = CustomItemRegistryPopulator.registerCustomItem(customItem, customItemId, palette.protocolVersion);

                    componentItemData.add(registration.componentItemData());
                    ItemMapping mapping = registration.mapping();
                    Item javaItem = registration.javaItem();
                    while (javaItem.javaId() >= mappings.size()) {
                        // Fill with empty to get to the correct size
                        mappings.add(ItemMapping.AIR);
                    }
                    mappings.set(javaItem.javaId(), mapping);
                    registry.put(customItemId, mapping.getBedrockDefinition());

                    if (customItem.creativeCategory().isPresent()) {
                        creativeItems.add(ItemData.builder()
                                .definition(registration.mapping().getBedrockDefinition())
                                .netId(creativeNetId.incrementAndGet())
                                .count(1)
                                .build());
                    }
                }
            }

            for (Map.Entry<String, Object> entry : vanillaComponents.entrySet()) {
                String id = entry.getKey();
                ItemDefinition definition = definitions.get(id);
                if (definition == null) {
                    // Newer item most likely
                    GeyserImpl.getInstance().getLogger().debug(
                            "Skipping vanilla component " + id + " for protocol " + palette.protocolVersion()
                    );
                    continue;
                }

                NbtMapBuilder root = NbtMap.builder()
                        .putString("name", id)
                        .putInt("id", definition.getRuntimeId())
                        .putCompound("components", (NbtMap) entry.getValue());

                componentItemData.add(new ComponentItemData(id, root.build()));
            }

            // Register the item forms of custom blocks
            if (BlockRegistries.CUSTOM_BLOCKS.get().length != 0) {
                for (CustomBlockData customBlock : BlockRegistries.CUSTOM_BLOCKS.get()) {
                    // We might've registered it already with the vanilla blocks so check first
                    if (customBlockItemDefinitions.containsKey(customBlock)) {
                        continue;
                    }

                    // Non-vanilla custom blocks will be handled in the item
                    // registry, so we don't need to do anything here.
                    if (customBlock instanceof NonVanillaCustomBlockData) {
                        continue;
                    }

                    int customProtocolId = nextFreeBedrockId++;
                    String identifier = customBlock.identifier();

                    final ItemDefinition definition = new SimpleItemDefinition(identifier, customProtocolId, true);
                    registry.put(customProtocolId, definition);
                    customBlockItemDefinitions.put(customBlock, definition);
                    customIdMappings.put(customProtocolId, identifier);

                    GeyserBedrockBlock bedrockBlock = blockMappings.getCustomBlockStateDefinitions().getOrDefault(customBlock.defaultBlockState(), null);

                    if (bedrockBlock != null && customBlock.includedInCreativeInventory()) {
                        creativeItems.add(ItemData.builder()
                                .definition(definition)
                                .blockDefinition(bedrockBlock)
                                .netId(creativeNetId.incrementAndGet())
                                .count(1)
                                .build());
                    }
                }
            }

            ItemMappings itemMappings = ItemMappings.builder()
                    .items(mappings.toArray(new ItemMapping[0]))
                    .creativeItems(creativeItems.toArray(new ItemData[0]))
                    .itemDefinitions(registry)
                    .storedItems(new StoredItemMappings(javaItemToMapping))
                    .javaOnlyItems(javaOnlyItems)
                    .buckets(buckets)
                    .componentItemData(componentItemData)
                    .lodestoneCompass(lodestoneEntry)
                    .customIdMappings(customIdMappings)
                    .customBlockItemDefinitions(customBlockItemDefinitions)
                    .build();

            Registries.ITEMS.register(palette.protocolVersion(), itemMappings);

            firstMappingsPass = false;
        }
    }

    private static void registerFurnaceMinecart(int nextFreeBedrockId, List<ComponentItemData> componentItemData, int protocolVersion) {
        NbtMapBuilder builder = NbtMap.builder();
        builder.putString("name", "geysermc:furnace_minecart")
                .putInt("id", nextFreeBedrockId);

        NbtMapBuilder itemProperties = NbtMap.builder();

        NbtMapBuilder componentBuilder = NbtMap.builder();
        // Conveniently, as of 1.16.200, the furnace minecart has a texture AND translation string already.
        // Not so conveniently, the way to set an icon changed in 1.20.60
        NbtMap iconMap = NbtMap.builder()
            .putCompound("textures", NbtMap.builder()
                    .putString("default", "minecart_furnace")
                    .build())
            .build();
        itemProperties.putCompound("minecraft:icon", iconMap);
        componentBuilder.putCompound("minecraft:display_name", NbtMap.builder().putString("value", "item.minecartFurnace.name").build());

        // Indicate that the arm animation should play on rails
        List<NbtMap> useOnTag = Collections.singletonList(NbtMap.builder().putString("tags", "q.any_tag('rail')").build());
        componentBuilder.putCompound("minecraft:entity_placer", NbtMap.builder()
                .putList("dispense_on", NbtType.COMPOUND, useOnTag)
                .putString("entity", "minecraft:minecart")
                .putList("use_on", NbtType.COMPOUND, useOnTag)
                .build());

        // We always want to allow offhand usage when we can - matches Java Edition
        itemProperties.putBoolean("allow_off_hand", true);
        itemProperties.putBoolean("hand_equipped", false);
        itemProperties.putInt("max_stack_size", 1);
        itemProperties.putString("creative_group", "itemGroup.name.minecart");
        itemProperties.putInt("creative_category", 4); // 4 - "Items"

        componentBuilder.putCompound("item_properties", itemProperties.build());
        builder.putCompound("components", componentBuilder.build());
        componentItemData.add(new ComponentItemData("geysermc:furnace_minecart", builder.build()));
    }
}
