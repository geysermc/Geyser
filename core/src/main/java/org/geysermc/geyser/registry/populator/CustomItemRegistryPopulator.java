/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

import com.google.common.collect.Multimap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ComponentItemData;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.item.custom.CustomBlockPlacer;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomRenderOffsets;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.event.type.GeyserDefineCustomItemsEventImpl;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.components.WearableSlot;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.type.GeyserMappingItem;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.registry.type.NonVanillaItemRegistration;

import java.util.*;

public class CustomItemRegistryPopulator {
    public static void populate(Map<String, GeyserMappingItem> items, Multimap<String, CustomItemData> customItems, List<NonVanillaCustomItemData> nonVanillaCustomItems) {
        MappingsConfigReader mappingsConfigReader = new MappingsConfigReader();
        // Load custom items from mappings files
        mappingsConfigReader.loadItemMappingsFromJson((key, item) -> {
            if (CustomItemRegistryPopulator.initialCheck(key, item, items)) {
                customItems.get(key).add(item);
            }
        });

        GeyserImpl.getInstance().eventBus().fire(new GeyserDefineCustomItemsEventImpl(customItems, nonVanillaCustomItems) {
            @Override
            public boolean register(@NonNull String identifier, @NonNull CustomItemData customItemData) {
                if (CustomItemRegistryPopulator.initialCheck(identifier, customItemData, items)) {
                    customItems.get(identifier).add(customItemData);
                    return true;
                }
                return false;
            }

            @Override
            public boolean register(@NonNull NonVanillaCustomItemData customItemData) {
                if (customItemData.identifier().startsWith("minecraft:")) {
                    GeyserImpl.getInstance().getLogger().error("The custom item " + customItemData.identifier() +
                        " is attempting to masquerade as a vanilla Minecraft item!");
                    return false;
                }

                if (customItemData.javaId() < items.size()) {
                    // Attempting to overwrite an item that already exists in the protocol
                    GeyserImpl.getInstance().getLogger().error("The custom item " + customItemData.identifier() +
                        " is attempting to overwrite a vanilla Minecraft item!");
                    return false;
                }

                nonVanillaCustomItems.add(customItemData);
                return true;
            }
        });

        int customItemCount = customItems.size() + nonVanillaCustomItems.size();
        if (customItemCount > 0) {
            GeyserImpl.getInstance().getLogger().info("Registered " + customItemCount + " custom items");
        }
    }

    public static GeyserCustomMappingData registerCustomItem(String customItemName, Item javaItem, GeyserMappingItem mapping, CustomItemData customItemData, int bedrockId, int protocolVersion) {
        ItemDefinition itemDefinition = new SimpleItemDefinition(customItemName, bedrockId, true);

        NbtMapBuilder builder = createComponentNbt(customItemData, javaItem, mapping, customItemName, bedrockId, protocolVersion);
        ComponentItemData componentItemData = new ComponentItemData(customItemName, builder.build());

        return new GeyserCustomMappingData(componentItemData, itemDefinition, customItemName, bedrockId);
    }

    static boolean initialCheck(String identifier, CustomItemData item, Map<String, GeyserMappingItem> mappings) {
        if (!mappings.containsKey(identifier)) {
            GeyserImpl.getInstance().getLogger().error("Could not find the Java item to add custom item properties to for " + item.name());
            return false;
        }
        if (!item.customItemOptions().hasCustomItemOptions()) {
            GeyserImpl.getInstance().getLogger().error("The custom item " + item.name() + " has no registration types");
        }
        String name = item.name();
        if (name.isEmpty()) {
            GeyserImpl.getInstance().getLogger().warning("Custom item name is empty?");
        } else if (Character.isDigit(name.charAt(0))) {
            // As of 1.19.31
            GeyserImpl.getInstance().getLogger().warning("Custom item name (" + name + ") begins with a digit. This may cause issues!");
        }
        return true;
    }

    public static NonVanillaItemRegistration registerCustomItem(NonVanillaCustomItemData customItemData, int customItemId, int protocolVersion) {
        String customIdentifier = customItemData.identifier();

        Set<String> repairMaterials = customItemData.repairMaterials();

        Item.Builder itemBuilder = Item.builder()
            .stackSize(customItemData.stackSize())
            .maxDamage(customItemData.maxDamage());
        Item item = new Item(customIdentifier, itemBuilder) {
            @Override
            public boolean isValidRepairItem(Item other) {
                return repairMaterials != null && repairMaterials.contains(other.javaIdentifier());
            }
        };
        Items.register(item, customItemData.javaId());

        ItemMapping customItemMapping = ItemMapping.builder()
            .bedrockDefinition(new SimpleItemDefinition(customIdentifier, customItemId, true))
            .bedrockData(0)
            .bedrockBlockDefinition(null)
            .toolType(customItemData.toolType())
            .toolTier(customItemData.toolTier())
            .translationString(customItemData.translationString())
            .customItemOptions(Collections.emptyList())
            .javaItem(item)
            .build();

        NbtMapBuilder builder = createComponentNbt(customItemData, customItemData.identifier(), customItemId,
            customItemData.isHat(), customItemData.displayHandheld(), protocolVersion);
        ComponentItemData componentItemData = new ComponentItemData(customIdentifier, builder.build());

        return new NonVanillaItemRegistration(componentItemData, item, customItemMapping);
    }

    private static NbtMapBuilder createComponentNbt(CustomItemData customItemData, Item javaItem, GeyserMappingItem mapping,
                                                    String customItemName, int customItemId, int protocolVersion) {
        NbtMapBuilder builder = NbtMap.builder();
        builder.putString("name", customItemName)
            .putInt("id", customItemId);

        NbtMapBuilder itemProperties = NbtMap.builder();
        NbtMapBuilder componentBuilder = NbtMap.builder();

        setupBasicItemInfo(javaItem.maxDamage(), javaItem.maxStackSize(), mapping.getToolType() != null || customItemData.displayHandheld(), false, customItemData, itemProperties, componentBuilder, protocolVersion);

        boolean canDestroyInCreative = true;
        if (mapping.getToolType() != null) { // This is not using the isTool boolean because it is not just a render type here.
            canDestroyInCreative = computeToolProperties(mapping.getToolType(), itemProperties, componentBuilder, javaItem.attackDamage());
        }
        itemProperties.putBoolean("can_destroy_in_creative", canDestroyInCreative);

        if (mapping.getArmorType() != null) {
            computeArmorProperties(mapping.getArmorType(), mapping.getProtectionValue(), itemProperties, componentBuilder);
        }

        if (mapping.getFirstBlockRuntimeId() != null) {
            computeBlockItemProperties(new CustomBlockPlacer(mapping.getBedrockIdentifier(), false), componentBuilder);
        }

        if (mapping.isEdible()) {
            computeConsumableProperties(itemProperties, componentBuilder, 1, false);
        }

        if (mapping.isEntityPlacer()) {
            computeEntityPlacerProperties(componentBuilder);
        }

        switch (mapping.getBedrockIdentifier()) {
            case "minecraft:fire_charge", "minecraft:flint_and_steel" -> computeBlockItemProperties(new CustomBlockPlacer("minecraft:fire", false), componentBuilder);
            case "minecraft:bow", "minecraft:crossbow", "minecraft:trident" -> computeChargeableProperties(itemProperties, componentBuilder, mapping.getBedrockIdentifier(), protocolVersion);
            case "minecraft:honey_bottle", "minecraft:milk_bucket", "minecraft:potion" -> computeConsumableProperties(itemProperties, componentBuilder, 2, true);
            case "minecraft:experience_bottle", "minecraft:egg", "minecraft:ender_pearl", "minecraft:ender_eye", "minecraft:lingering_potion", "minecraft:snowball", "minecraft:splash_potion" ->
                computeThrowableProperties(componentBuilder);
        }

        // Hardcoded on Java, and should extend to the custom item
        boolean isHat = (javaItem.equals(Items.SKELETON_SKULL) || javaItem.equals(Items.WITHER_SKELETON_SKULL)
            || javaItem.equals(Items.CARVED_PUMPKIN) || javaItem.equals(Items.ZOMBIE_HEAD)
            || javaItem.equals(Items.PIGLIN_HEAD) || javaItem.equals(Items.DRAGON_HEAD)
            || javaItem.equals(Items.CREEPER_HEAD) || javaItem.equals(Items.PLAYER_HEAD)
        );
        computeRenderOffsets(isHat, customItemData, componentBuilder);

        componentBuilder.putCompound("item_properties", itemProperties.build());
        builder.putCompound("components", componentBuilder.build());

        return builder;
    }

    private static NbtMapBuilder createComponentNbt(NonVanillaCustomItemData customItemData, String customItemName,
                                                    int customItemId, boolean isHat, boolean displayHandheld, int protocolVersion) {
        NbtMapBuilder builder = NbtMap.builder();
        builder.putString("name", customItemName)
            .putInt("id", customItemId);

        NbtMapBuilder itemProperties = NbtMap.builder();
        NbtMapBuilder componentBuilder = NbtMap.builder();

        // this can replace minecraft:icon, so we have to add this here.
        boolean replaceBlockItem = customItemData.blockPlacer() != null && customItemData.blockPlacer().replaceBlockItem();

        setupBasicItemInfo(customItemData.maxDamage(), customItemData.stackSize(), displayHandheld, replaceBlockItem, customItemData, itemProperties, componentBuilder, protocolVersion);

        boolean canDestroyInCreative = true;
        if (customItemData.toolType() != null) { // This is not using the isTool boolean because it is not just a render type here.
            canDestroyInCreative = computeToolProperties(Objects.requireNonNull(customItemData.toolType()), itemProperties, componentBuilder, customItemData.attackDamage());
        }
        itemProperties.putBoolean("can_destroy_in_creative", canDestroyInCreative);

        String armorType = customItemData.armorType();
        if (armorType != null) {
            computeArmorProperties(armorType, customItemData.protectionValue(), itemProperties, componentBuilder);
        }

        if (customItemData.isEdible()) {
            computeConsumableProperties(itemProperties, componentBuilder, 1, customItemData.canAlwaysEat());
        }

        if (customItemData.isChargeable()) {
            String tooltype = customItemData.toolType();
            if (tooltype == null) {
                throw new IllegalArgumentException("tool type must be set if the custom item is chargeable!");
            }
            computeChargeableProperties(itemProperties, componentBuilder, "minecraft:" + tooltype, protocolVersion);
        }

        computeRenderOffsets(isHat, customItemData, componentBuilder);

        if (customItemData.isFoil()) {
            itemProperties.putBoolean("foil", true);
        }

        CustomBlockPlacer blockPlacer = customItemData.blockPlacer();
        if (blockPlacer != null) {
            computeBlockItemProperties(blockPlacer, componentBuilder);
        }

        componentBuilder.putCompound("item_properties", itemProperties.build());
        builder.putCompound("components", componentBuilder.build());

        return builder;
    }

    private static void setupBasicItemInfo(int maxDamage, int stackSize, boolean displayHandheld, boolean replaceBlockItem, CustomItemData customItemData, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder, int protocolVersion) {
        if (!replaceBlockItem) {
            NbtMap iconMap = NbtMap.builder()
                .putCompound("textures", NbtMap.builder()
                    .putString("default", customItemData.icon())
                    .build())
                .build();
            itemProperties.putCompound("minecraft:icon", iconMap);
        }

        if (customItemData.creativeCategory().isPresent()) {
            itemProperties.putInt("creative_category", customItemData.creativeCategory().getAsInt());

            if (customItemData.creativeGroup() != null) {
                itemProperties.putString("creative_group", customItemData.creativeGroup());
            }
        }

        componentBuilder.putCompound("minecraft:display_name", NbtMap.builder().putString("value", customItemData.displayName()).build());

        // Add a Geyser tag to the item, allowing Molang queries
        addItemTag(componentBuilder, "geyser:is_custom");

        // Add other defined tags to the item
        Set<String> tags = customItemData.tags();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                addItemTag(componentBuilder, tag);
            }
        }

        itemProperties.putBoolean("allow_off_hand", customItemData.allowOffhand());
        itemProperties.putBoolean("hand_equipped", displayHandheld);
        itemProperties.putInt("max_stack_size", stackSize);
        // Ignore durability if the item's predicate requires that it be unbreakable
        if (maxDamage > 0 && customItemData.customItemOptions().unbreakable() != TriState.TRUE) {
            componentBuilder.putCompound("minecraft:durability", NbtMap.builder()
                .putCompound("damage_chance", NbtMap.builder()
                    .putInt("max", 1)
                    .putInt("min", 1)
                    .build())
                .putInt("max_durability", maxDamage)
                .build());
            itemProperties.putBoolean("use_duration", true);
        }
    }

    /**
     * @return can destroy in creative
     */
    private static boolean computeToolProperties(String toolType, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder, int attackDamage) {
        boolean canDestroyInCreative = true;
        float miningSpeed = 1.0f;

        // This means client side the tool can never destroy a block
        // This works because the molang '1' for tags will be true for all blocks and the speed will be 0
        // We want this since we calculate break speed server side in BedrockActionTranslator
        List<NbtMap> speed = new ArrayList<>(List.of(
            NbtMap.builder()
                .putCompound("block", NbtMap.builder()
                    .putString("tags", "1")
                    .build())
                .putCompound("on_dig", NbtMap.builder()
                    .putCompound("condition", NbtMap.builder()
                        .putString("expression", "")
                        .putInt("version", -1)
                        .build())
                    .putString("event", "tool_durability")
                    .putString("target", "self")
                    .build())
                .putInt("speed", 0)
                .build()
        ));

        componentBuilder.putCompound("minecraft:digger",
            NbtMap.builder()
                .putList("destroy_speeds", NbtType.COMPOUND, speed)
                .putCompound("on_dig", NbtMap.builder()
                    .putCompound("condition", NbtMap.builder()
                        .putString("expression", "")
                        .putInt("version", -1)
                        .build())
                    .putString("event", "tool_durability")
                    .putString("target", "self")
                    .build())
                .putBoolean("use_efficiency", true)
                .build()
        );

        if (toolType.equals("sword")) {
            miningSpeed = 1.5f;
            canDestroyInCreative = false;
        }

        itemProperties.putBoolean("hand_equipped", true);
        itemProperties.putFloat("mining_speed", miningSpeed);

        // This allows custom tools - shears, swords, shovels, axes etc to be enchanted or combined in the anvil
        itemProperties.putInt("enchantable_value", 1);
        itemProperties.putString("enchantable_slot", toolType);

        // Adds a "attack damage" indicator. Purely visual!
        if (attackDamage > 0) {
            itemProperties.putInt("damage", attackDamage);
        }

        return canDestroyInCreative;
    }

    private static void computeArmorProperties(String armorType, int protectionValue, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder) {
        switch (armorType) {
            case "boots" -> {
                componentBuilder.putString("minecraft:render_offsets", "boots");
                componentBuilder.putCompound("minecraft:wearable", WearableSlot.FEET.getSlotNbt());
                componentBuilder.putCompound("minecraft:armor", NbtMap.builder().putInt("protection", protectionValue).build());

                itemProperties.putString("enchantable_slot", "armor_feet");
                itemProperties.putInt("enchantable_value", 15);
            }
            case "chestplate" -> {
                componentBuilder.putString("minecraft:render_offsets", "chestplates");
                componentBuilder.putCompound("minecraft:wearable", WearableSlot.CHEST.getSlotNbt());
                componentBuilder.putCompound("minecraft:armor", NbtMap.builder().putInt("protection", protectionValue).build());

                itemProperties.putString("enchantable_slot", "armor_torso");
                itemProperties.putInt("enchantable_value", 15);
            }
            case "leggings" -> {
                componentBuilder.putString("minecraft:render_offsets", "leggings");
                componentBuilder.putCompound("minecraft:wearable", WearableSlot.LEGS.getSlotNbt());
                componentBuilder.putCompound("minecraft:armor", NbtMap.builder().putInt("protection", protectionValue).build());

                itemProperties.putString("enchantable_slot", "armor_legs");
                itemProperties.putInt("enchantable_value", 15);
            }
            case "helmet" -> {
                componentBuilder.putString("minecraft:render_offsets", "helmets");
                componentBuilder.putCompound("minecraft:wearable", WearableSlot.HEAD.getSlotNbt());
                componentBuilder.putCompound("minecraft:armor", NbtMap.builder().putInt("protection", protectionValue).build());

                itemProperties.putString("enchantable_slot", "armor_head");
                itemProperties.putInt("enchantable_value", 15);
            }
        }
    }

    private static void computeBlockItemProperties(CustomBlockPlacer blockPlacer, NbtMapBuilder componentBuilder) {
        // carved pumpkin should be able to be worn and for that we would need to add wearable and armor with protection 0 here
        // however this would have the side effect of preventing carved pumpkins from working as an attachable on the RP side outside the head slot
        // it also causes the item to glitch when right clicked to "equip" so this should only be added here later if these issues can be overcome

        // all block items registered should be given this component to prevent double placement
        componentBuilder.putCompound("minecraft:block_placer",
            NbtMap.builder()
                .putString("block", blockPlacer.block())
                .putBoolean("replace_block_item", blockPlacer.replaceBlockItem())
                .build());
    }

    private static void computeChargeableProperties(NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder, String mapping, int protocolVersion) {
        // setting high use_duration prevents the consume animation from playing
        itemProperties.putInt("use_duration", Integer.MAX_VALUE);
        // display item as tool (mainly for crossbow and bow)
        itemProperties.putBoolean("hand_equipped", true);
        // Make bows, tridents, and crossbows enchantable
        itemProperties.putInt("enchantable_value", 1);

        componentBuilder.putCompound("minecraft:use_modifiers", NbtMap.builder()
            .putFloat("use_duration", 100F)
            .putFloat("movement_modifier", 0.35F)
            .build());

        switch (mapping) {
            case "minecraft:bow" -> {
                itemProperties.putString("enchantable_slot", "bow");
                itemProperties.putInt("frame_count", 3);

                componentBuilder.putCompound("minecraft:shooter", NbtMap.builder()
                    .putList("ammunition", NbtType.COMPOUND, List.of(
                        NbtMap.builder()
                            .putCompound("item", NbtMap.builder()
                                .putString("name", "minecraft:arrow")
                                .build())
                            .putBoolean("use_offhand", true)
                            .putBoolean("search_inventory", true)
                            .build()
                    ))
                    .putFloat("max_draw_duration", 0f)
                    .putBoolean("charge_on_draw", true)
                    .putBoolean("scale_power_by_draw_duration", true)
                    .build());
                componentBuilder.putInt("minecraft:use_duration", 999);
            }
            case "minecraft:trident" -> {
                itemProperties.putString("enchantable_slot", "trident");
                componentBuilder.putInt("minecraft:use_duration", 999);
            }
            case "minecraft:crossbow" -> {
                itemProperties.putString("enchantable_slot", "crossbow");
                itemProperties.putInt("frame_count", 10);

                componentBuilder.putCompound("minecraft:shooter", NbtMap.builder()
                    .putList("ammunition", NbtType.COMPOUND, List.of(
                        NbtMap.builder()
                            .putCompound("item", NbtMap.builder()
                                .putString("name", "minecraft:arrow")
                                .build())
                            .putBoolean("use_offhand", true)
                            .putBoolean("search_inventory", true)
                            .build()
                    ))
                    .putFloat("max_draw_duration", 1f)
                    .putBoolean("charge_on_draw", true)
                    .putBoolean("scale_power_by_draw_duration", true)
                    .build());
                componentBuilder.putInt("minecraft:use_duration", 999);
            }
        }
    }

    private static void computeConsumableProperties(NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder, int useAnimation, boolean canAlwaysEat) {
        // this is the duration of the use animation in ticks; note that in behavior packs this is set as a float in seconds, but over the network it is an int in ticks
        itemProperties.putInt("use_duration", 32);
        // this dictates that the item will use the eat or drink animation (in the first person) and play eat or drink sounds
        // note that in behavior packs this is set as the string "eat" or "drink", but over the network it as an int, with these values being 1 and 2 respectively
        itemProperties.putInt("use_animation", useAnimation);
        // this component is required to allow the eat animation to play
        componentBuilder.putCompound("minecraft:food", NbtMap.builder().putBoolean("can_always_eat", canAlwaysEat).build());
    }

    private static void computeEntityPlacerProperties(NbtMapBuilder componentBuilder) {
        // all items registered that place entities should be given this component to prevent double placement
        // it is okay that the entity here does not match the actual one since we control what entity actually spawns
        componentBuilder.putCompound("minecraft:entity_placer", NbtMap.builder().putString("entity", "minecraft:minecart").build());
    }

    private static void computeThrowableProperties(NbtMapBuilder componentBuilder) {
        // allows item to be thrown when holding down right click (individual presses are required w/o this component)
        componentBuilder.putCompound("minecraft:throwable", NbtMap.builder().putBoolean("do_swing_animation", true).build());
        // this must be set to something for the swing animation to play
        // it is okay that the projectile here does not match the actual one since we control what entity actually spawns
        componentBuilder.putCompound("minecraft:projectile", NbtMap.builder().putString("projectile_entity", "minecraft:snowball").build());
    }

    private static void computeRenderOffsets(boolean isHat, CustomItemData customItemData, NbtMapBuilder componentBuilder) {
        if (isHat) {
            componentBuilder.remove("minecraft:render_offsets");
            componentBuilder.putString("minecraft:render_offsets", "helmets");

            componentBuilder.remove("minecraft:wearable");
            componentBuilder.putCompound("minecraft:wearable", WearableSlot.HEAD.getSlotNbt());
        }

        CustomRenderOffsets renderOffsets = customItemData.renderOffsets();
        if (renderOffsets != null) {
            componentBuilder.remove("minecraft:render_offsets");
            componentBuilder.putCompound("minecraft:render_offsets", toNbtMap(renderOffsets));
        } else if (customItemData.textureSize() != 16 && !componentBuilder.containsKey("minecraft:render_offsets")) {
            float scale1 = (float) (0.075 / (customItemData.textureSize() / 16f));
            float scale2 = (float) (0.125 / (customItemData.textureSize() / 16f));
            float scale3 = (float) (0.075 / (customItemData.textureSize() / 16f * 2.4f));

            componentBuilder.putCompound("minecraft:render_offsets",
                NbtMap.builder().putCompound("main_hand", NbtMap.builder()
                        .putCompound("first_person", xyzToScaleList(scale3, scale3, scale3))
                        .putCompound("third_person", xyzToScaleList(scale1, scale2, scale1)).build())
                    .putCompound("off_hand", NbtMap.builder()
                        .putCompound("first_person", xyzToScaleList(scale1, scale2, scale1))
                        .putCompound("third_person", xyzToScaleList(scale1, scale2, scale1)).build()).build());
        }
    }

    private static NbtMap toNbtMap(CustomRenderOffsets renderOffsets) {
        NbtMapBuilder builder = NbtMap.builder();

        CustomRenderOffsets.Hand mainHand = renderOffsets.mainHand();
        if (mainHand != null) {
            NbtMap nbt = toNbtMap(mainHand);
            if (nbt != null) {
                builder.putCompound("main_hand", nbt);
            }
        }
        CustomRenderOffsets.Hand offhand = renderOffsets.offhand();
        if (offhand != null) {
            NbtMap nbt = toNbtMap(offhand);
            if (nbt != null) {
                builder.putCompound("off_hand", nbt);
            }
        }

        return builder.build();
    }

    private static @Nullable NbtMap toNbtMap(CustomRenderOffsets.Hand hand) {
        NbtMap firstPerson = toNbtMap(hand.firstPerson());
        NbtMap thirdPerson = toNbtMap(hand.thirdPerson());

        if (firstPerson == null && thirdPerson == null) {
            return null;
        }

        NbtMapBuilder builder = NbtMap.builder();
        if (firstPerson != null) {
            builder.putCompound("first_person", firstPerson);
        }
        if (thirdPerson != null) {
            builder.putCompound("third_person", thirdPerson);
        }

        return builder.build();
    }

    private static @Nullable NbtMap toNbtMap(CustomRenderOffsets.@Nullable Offset offset) {
        if (offset == null) {
            return null;
        }

        CustomRenderOffsets.OffsetXYZ position = offset.position();
        CustomRenderOffsets.OffsetXYZ rotation = offset.rotation();
        CustomRenderOffsets.OffsetXYZ scale = offset.scale();

        if (position == null && rotation == null && scale == null) {
            return null;
        }

        NbtMapBuilder builder = NbtMap.builder();
        if (position != null) {
            builder.putList("position", NbtType.FLOAT, toList(position));
        }
        if (rotation != null) {
            builder.putList("rotation", NbtType.FLOAT, toList(rotation));
        }
        if (scale != null) {
            builder.putList("scale", NbtType.FLOAT, toList(scale));
        }

        return builder.build();
    }

    private static List<Float> toList(CustomRenderOffsets.OffsetXYZ xyz) {
        return List.of(xyz.x(), xyz.y(), xyz.z());
    }

    @SuppressWarnings("unchecked")
    private static void addItemTag(NbtMapBuilder builder, String tag) {
        List<String> tagList = (List<String>) builder.get("item_tags");
        if (tagList == null) {
            builder.putList("item_tags", NbtType.STRING, tag);
        } else {
            // NbtList is immutable
            if (!tagList.contains(tag)) {
                tagList = new ArrayList<>(tagList);
                tagList.add(tag);
                builder.putList("item_tags", NbtType.STRING, tagList);
            }
        }
    }

    private static NbtMap xyzToScaleList(float x, float y, float z) {
        return NbtMap.builder().putList("scale", NbtType.FLOAT, List.of(x, y, z)).build();
    }
}
