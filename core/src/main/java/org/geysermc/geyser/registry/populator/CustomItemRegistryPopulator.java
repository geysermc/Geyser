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
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.exception.CustomItemDefinitionRegisterException;
import org.geysermc.geyser.api.item.custom.CustomRenderOffsets;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.DataComponent;
import org.geysermc.geyser.api.item.custom.v2.component.Repairable;
import org.geysermc.geyser.api.item.custom.v2.component.ToolProperties;
import org.geysermc.geyser.api.item.custom.v2.predicate.CustomItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.condition.ConditionPredicateProperty;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.event.type.GeyserDefineCustomItemsEventImpl;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.custom.ComponentConverters;
import org.geysermc.geyser.item.custom.predicate.ConditionPredicate;
import org.geysermc.geyser.item.exception.InvalidItemComponentsException;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.type.GeyserMappingItem;
import org.geysermc.geyser.registry.type.NonVanillaItemRegistration;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Consumable;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Equippable;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.FoodProperties;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.UseCooldown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CustomItemRegistryPopulator {
    private static final Identifier UNBREAKABLE_COMPONENT = Identifier.of("minecraft", "unbreakable");

    // In behaviour packs and Java components this is set to a text value, such as "eat" or "drink"; over Bedrock network it's sent as an int.
    // These don't all work correctly on Bedrock - see the Consumable.Animation Javadoc in the API
    private static final Map<Consumable.ItemUseAnimation, Integer> BEDROCK_ANIMATIONS = Map.of(
        Consumable.ItemUseAnimation.NONE, 0,
        Consumable.ItemUseAnimation.EAT, 1,
        Consumable.ItemUseAnimation.DRINK, 2,
        Consumable.ItemUseAnimation.BLOCK, 3,
        Consumable.ItemUseAnimation.BOW, 4,
        Consumable.ItemUseAnimation.SPEAR, 6,
        Consumable.ItemUseAnimation.CROSSBOW, 9,
        Consumable.ItemUseAnimation.SPYGLASS, 10,
        Consumable.ItemUseAnimation.BRUSH, 12
    );

    public static void populate(Map<String, GeyserMappingItem> items, Multimap<Identifier, CustomItemDefinition> customItems, List<NonVanillaCustomItemData> nonVanillaCustomItems) {
        MappingsConfigReader mappingsConfigReader = new MappingsConfigReader();
        // Load custom items from mappings files
        mappingsConfigReader.loadItemMappingsFromJson((identifier, item) -> {
            String error = validate(identifier, item, customItems, items);
            if (error == null) {
                customItems.get(identifier).add(item);
            } else {
                GeyserImpl.getInstance().getLogger().error("Not registering custom item definition (bedrock identifier=" + item.bedrockIdentifier() + "): " + error);
            }
        });

        GeyserImpl.getInstance().eventBus().fire(new GeyserDefineCustomItemsEventImpl(customItems, nonVanillaCustomItems) {

            @Override
            public void register(@NonNull Identifier identifier, @NonNull CustomItemDefinition definition) throws CustomItemDefinitionRegisterException {
                String error = validate(identifier, definition, customItems, items);
                if (error == null) {
                    customItems.get(identifier).add(definition);
                } else {
                    throw new CustomItemDefinitionRegisterException("Not registering custom item definition (bedrock identifier=" + definition.bedrockIdentifier() + "): " + error);
                }
            }

            @Override
            public boolean register(@NonNull NonVanillaCustomItemData customItemData) {
                // TODO
                return false;
            }
        });

        int customItemCount = customItems.size() + nonVanillaCustomItems.size();
        if (customItemCount > 0) {
            GeyserImpl.getInstance().getLogger().info("Registered " + customItemCount + " custom items");
        }
    }

    public static GeyserCustomMappingData registerCustomItem(Item javaItem, GeyserMappingItem mapping, CustomItemDefinition customItem,
                                                             int bedrockId, int protocolVersion) throws InvalidItemComponentsException {
        checkComponents(customItem, javaItem);

        NbtMapBuilder builder = createComponentNbt(customItem, javaItem, mapping, bedrockId, protocolVersion);
        ItemDefinition itemDefinition = new SimpleItemDefinition(customItem.bedrockIdentifier().toString(), bedrockId, ItemVersion.DATA_DRIVEN, true, builder.build());

        return new GeyserCustomMappingData(customItem, itemDefinition, bedrockId);
    }

    /**
     * @return null if there are no errors with the registration, and an error message if there are
     */
    private static String validate(Identifier vanillaIdentifier, CustomItemDefinition item, Multimap<Identifier, CustomItemDefinition> registered, Map<String, GeyserMappingItem> mappings) {
        if (!mappings.containsKey(vanillaIdentifier.toString())) {
            return "unknown Java item " + vanillaIdentifier;
        }
        Identifier bedrockIdentifier = item.bedrockIdentifier();
        if (bedrockIdentifier.namespace().equals(Key.MINECRAFT_NAMESPACE)) {
            return "custom item bedrock identifier namespace can't be minecraft";
        } else if (item.model().namespace().equals(Key.MINECRAFT_NAMESPACE) && item.predicates().isEmpty()) {
            return "custom item definition model can't be in the minecraft namespace without a predicate";
        }

        for (Map.Entry<Identifier, CustomItemDefinition> entry : registered.entries()) {
            if (entry.getValue().bedrockIdentifier().equals(item.bedrockIdentifier())) {
                return "conflicts with another custom item definition with the same bedrock identifier";
            }
            String error = checkPredicate(entry, vanillaIdentifier, item);
            if (error != null) {
                return "conflicts with custom item definition (bedrock identifier=" + entry.getValue().bedrockIdentifier() + "): " + error;
            }
        }

        return null;
    }

    /**
     * @return an error message if there was a conflict, or null otherwise
     */
    private static String checkPredicate(Map.Entry<Identifier, CustomItemDefinition> existing, Identifier vanillaIdentifier, CustomItemDefinition newItem) {
        // If the definitions are for different Java items or models then it doesn't matter
        if (!vanillaIdentifier.equals(existing.getKey()) || !newItem.model().equals(existing.getValue().model())) {
            return null;
        }
        // If they both don't have predicates they conflict
        if (existing.getValue().predicates().isEmpty() && newItem.predicates().isEmpty()) {
            return "both entries don't have predicates, one must have a predicate";
        }
        // If their predicates are equal then they also conflict
        if (existing.getValue().predicates().size() == newItem.predicates().size()) {
            boolean equal = true;
            for (CustomItemPredicate predicate : existing.getValue().predicates()) {
                if (!newItem.predicates().contains(predicate)) {
                    equal = false;
                }
            }
            if (equal) {
                return "both entries have the same predicates";
            }
        }

        return null;
    }

    /**
     * Check for illegal combinations of item components that can be specified in the custom item API, and validated components that can't be checked in the API, e.g. components that reference items.
     *
     * <p>Note that, component validation is preferred to occur early in the API module. This method should primarily check for illegal <em>combinations</em> of item components.
     * It is expected that the values of the components separately have already been validated when possible (for example, it is expected that stack size is in the range [1, 99]).</p>
     */
    private static void checkComponents(CustomItemDefinition definition, Item javaItem) throws InvalidItemComponentsException {
        DataComponents components = patchDataComponents(javaItem, definition);
        int stackSize = components.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, 0);
        int maxDamage = components.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);

        if (components.get(DataComponentTypes.EQUIPPABLE) != null && stackSize > 1) {
            throw new InvalidItemComponentsException("Bedrock doesn't support equippable items with a stack size above 1");
        } else if (stackSize > 1 && maxDamage > 0) {
            throw new InvalidItemComponentsException("Stack size must be 1 when max damage is above 0");
        }

        Repairable repairable = definition.components().get(DataComponent.REPAIRABLE);
        if (repairable != null) {
            for (Identifier item : repairable.items()) {
                if (Registries.JAVA_ITEM_IDENTIFIERS.get(item.toString()) == null) {
                    throw new InvalidItemComponentsException("Unknown repair item " + item + " in minecraft:repairable component");
                }
            }
        }
    }

    public static NonVanillaItemRegistration registerCustomItem(NonVanillaCustomItemData customItemData, int customItemId, int protocolVersion) {
        // TODO
        return null;
    }

    private static NbtMapBuilder createComponentNbt(CustomItemDefinition customItemDefinition, Item vanillaJavaItem, GeyserMappingItem vanillaMapping,
                                                    int customItemId, int protocolVersion) {
        NbtMapBuilder builder = NbtMap.builder()
            .putString("name", customItemDefinition.bedrockIdentifier().toString())
            .putInt("id", customItemId);

        NbtMapBuilder itemProperties = NbtMap.builder();
        NbtMapBuilder componentBuilder = NbtMap.builder();

        DataComponents components = patchDataComponents(vanillaJavaItem, customItemDefinition);
        setupBasicItemInfo(customItemDefinition, components, itemProperties, componentBuilder);

        computeToolProperties(itemProperties, componentBuilder);

        // Temporary workaround: when 1.21.5 releases, this value will be mapped to an MCPL tool component, and this code will look nicer
        // since we can get the value from the vanilla item component instead of using the vanilla mapping.
        ToolProperties toolProperties = customItemDefinition.components().get(DataComponent.TOOL);
        boolean canDestroyInCreative = toolProperties == null ? !"sword".equals(vanillaMapping.getToolType()) : toolProperties.canDestroyBlocksInCreative();
        computeCreativeDestroyProperties(canDestroyInCreative, itemProperties, componentBuilder);

        switch (vanillaMapping.getBedrockIdentifier()) {
            case "minecraft:fire_charge", "minecraft:flint_and_steel" -> computeBlockItemProperties("minecraft:fire", componentBuilder);
            case "minecraft:bow", "minecraft:crossbow", "minecraft:trident" -> computeChargeableProperties(itemProperties, componentBuilder, vanillaMapping.getBedrockIdentifier());
            case "minecraft:experience_bottle", "minecraft:egg", "minecraft:ender_pearl", "minecraft:ender_eye", "minecraft:lingering_potion", "minecraft:snowball", "minecraft:splash_potion" -> computeThrowableProperties(componentBuilder);
        }

        // Using API component here because MCPL one is just an ID holder set
        Repairable repairable = customItemDefinition.components().get(DataComponent.REPAIRABLE);
        if (repairable != null) {
            computeRepairableProperties(repairable, componentBuilder);
        }

        Equippable equippable = components.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            computeArmorProperties(equippable, customItemDefinition.bedrockOptions().protectionValue(), componentBuilder);
        }

        Integer enchantmentValue = components.get(DataComponentTypes.ENCHANTABLE);
        if (enchantmentValue != null) {
            computeEnchantableProperties(enchantmentValue, itemProperties, componentBuilder);
        }

        if (vanillaMapping.getFirstBlockRuntimeId() != null) {
            computeBlockItemProperties(vanillaMapping.getBedrockIdentifier(), componentBuilder);
        }

        Consumable consumable = components.get(DataComponentTypes.CONSUMABLE);
        if (consumable != null) {
            FoodProperties foodProperties = components.get(DataComponentTypes.FOOD);
            computeConsumableProperties(consumable, foodProperties, itemProperties, componentBuilder);
        }

        if (vanillaMapping.isEntityPlacer()) {
            computeEntityPlacerProperties(componentBuilder);
        }

        UseCooldown useCooldown = components.get(DataComponentTypes.USE_COOLDOWN);
        if (useCooldown != null) {
            computeUseCooldownProperties(useCooldown, componentBuilder);
        }

        componentBuilder.putCompound("item_properties", itemProperties.build());
        builder.putCompound("components", componentBuilder.build());

        return builder;
    }

    private static NbtMapBuilder createComponentNbt(NonVanillaCustomItemData customItemData, String customItemName,
                                                    int customItemId, boolean isHat, boolean displayHandheld, int protocolVersion) {
        // TODO;
        return null;
    }

    private static void setupBasicItemInfo(CustomItemDefinition definition, DataComponents components, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder) {
        CustomItemBedrockOptions options = definition.bedrockOptions();
        NbtMap iconMap = NbtMap.builder()
            .putCompound("textures", NbtMap.builder()
                .putString("default", definition.icon())
                .build())
            .build();
        itemProperties.putCompound("minecraft:icon", iconMap);

        if (options.creativeCategory() != CreativeCategory.NONE) {
            itemProperties.putInt("creative_category", options.creativeCategory().id());

            if (options.creativeGroup() != null) {
                itemProperties.putString("creative_group", options.creativeGroup());
            }
        }

        componentBuilder.putCompound("minecraft:display_name", NbtMap.builder().putString("value", definition.displayName()).build());

        // Add a Geyser tag to the item, allowing Molang queries
        addItemTag(componentBuilder, Identifier.of("geyser:is_custom"));

        // Add other defined tags to the item
        Set<Identifier> tags = options.tags();
        for (Identifier tag : tags) {
            addItemTag(componentBuilder, tag);
        }

        itemProperties.putBoolean("allow_off_hand", options.allowOffhand());
        itemProperties.putBoolean("hand_equipped", options.displayHandheld());

        int maxDamage = components.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
        Equippable equippable = components.get(DataComponentTypes.EQUIPPABLE);
        // Java requires stack size to be 1 when max damage is above 0, and bedrock requires stack size to be 1 when the item can be equipped
        int stackSize = maxDamage > 0 || equippable != null ? 1 : components.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, 0); // This should never be 0 since we're patching components on top of the vanilla ones

        itemProperties.putInt("max_stack_size", stackSize);
        if (maxDamage > 0 && !isUnbreakableItem(definition)) {
            componentBuilder.putCompound("minecraft:durability", NbtMap.builder()
                .putCompound("damage_chance", NbtMap.builder()
                    .putInt("max", 1)
                    .putInt("min", 1)
                    .build())
                .putInt("max_durability", maxDamage)
                .build());
        }
    }

    /**
     * Adds properties to make the Bedrock client unable to destroy any block with this custom item.
     * This works because the molang '1' for tags will be true for all blocks and the speed will be 0.
     * We want this since we calculate break speed server side in BedrockActionTranslator
     */
    private static void computeToolProperties(NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder) {
        List<NbtMap> speed = new ArrayList<>(List.of(
            NbtMap.builder()
                .putCompound("block", NbtMap.builder()
                    .putString("name", "")
                    .putCompound("states", NbtMap.EMPTY)
                    .putString("tags", "1")
                    .build())
                .putInt("speed", 0)
                .build()
        ));

        componentBuilder.putCompound("minecraft:digger", NbtMap.builder()
            .putList("destroy_speeds", NbtType.COMPOUND, speed)
            .putBoolean("use_efficiency", false)
            .build());

        itemProperties.putFloat("mining_speed", 1.0F);
    }

    private static void computeCreativeDestroyProperties(boolean canDestroyInCreative, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder) {
        itemProperties.putBoolean("can_destroy_in_creative", canDestroyInCreative);
        componentBuilder.putCompound("minecraft:can_destroy_in_creative", NbtMap.builder()
            .putBoolean("value", canDestroyInCreative)
            .build());
    }

    /**
     * Repairable component should already have been validated for valid Java items in {@link CustomItemRegistryPopulator#checkComponents(CustomItemDefinition, Item)}.
     *
     * <p>This method passes the Java identifiers straight to bedrock - which isn't perfect.</p>
     */
    private static void computeRepairableProperties(Repairable repairable, NbtMapBuilder componentBuilder) {
        List<NbtMap> items = Arrays.stream(repairable.items())
            .map(identifier -> NbtMap.builder()
                .putString("name", identifier.toString())
                .build()).toList();

        componentBuilder.putCompound("minecraft:repairable", NbtMap.builder()
            .putList("repair_items", NbtType.COMPOUND, NbtMap.builder()
                .putList("items", NbtType.COMPOUND, items)
                .putFloat("repair_amount", 0.0F)
                .build())
            .build());
    }

    private static void computeArmorProperties(Equippable equippable, int protectionValue, NbtMapBuilder componentBuilder) {
        switch (equippable.slot()) {
            case HELMET -> {
                componentBuilder.putCompound("minecraft:wearable", NbtMap.builder()
                    .putString("slot", "slot.armor.head")
                    .putInt("protection", protectionValue)
                    .build());
            }
            case CHESTPLATE -> {
                componentBuilder.putCompound("minecraft:wearable", NbtMap.builder()
                    .putString("slot", "slot.armor.chest")
                    .putInt("protection", protectionValue)
                    .build());
            }
            case LEGGINGS -> {
                componentBuilder.putCompound("minecraft:wearable", NbtMap.builder()
                    .putString("slot", "slot.armor.legs")
                    .putInt("protection", protectionValue)
                    .build());
            }
            case BOOTS -> {
                componentBuilder.putCompound("minecraft:wearable", NbtMap.builder()
                    .putString("slot", "slot.armor.feet")
                    .putInt("protection", protectionValue)
                    .build());
            }
        }
    }

    private static void computeEnchantableProperties(int enchantmentValue, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder) {
        itemProperties.putString("enchantable_slot", "all");
        itemProperties.putInt("enchantable_value", enchantmentValue);
        componentBuilder.putCompound("minecraft:enchantable", NbtMap.builder()
            .putString("slot", "all")
            .putByte("value", (byte) enchantmentValue)
            .build());
    }

    private static void computeBlockItemProperties(String blockItem, NbtMapBuilder componentBuilder) {
        // carved pumpkin should be able to be worn and for that we would need to add wearable and armor with protection 0 here
        // however this would have the side effect of preventing carved pumpkins from working as an attachable on the RP side outside the head slot
        // it also causes the item to glitch when right-clicked to "equip" so this should only be added here later if these issues can be overcome

        // all block items registered should be given this component to prevent double placement
        componentBuilder.putCompound("minecraft:block_placer", NbtMap.builder()
            .putString("block", blockItem)
            .putBoolean("canUseBlockAsIcon", false)
            .putList("use_on", NbtType.STRING)
            .build());
    }

    private static void computeChargeableProperties(NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder, String mapping) {
        // setting high use_duration prevents the consume animation from playing
        itemProperties.putInt("use_duration", Integer.MAX_VALUE);

        componentBuilder.putCompound("minecraft:use_modifiers", NbtMap.builder()
            .putFloat("movement_modifier", 0.35F)
            .putFloat("use_duration", 100.0F)
            .build());

        switch (mapping) {
            case "minecraft:bow" -> {
                itemProperties.putInt("frame_count", 3);

                componentBuilder.putCompound("minecraft:shooter", NbtMap.builder()
                    .putList("ammunition", NbtType.COMPOUND, List.of(
                        NbtMap.builder()
                            .putCompound("item", NbtMap.builder()
                                .putString("name", "minecraft:arrow")
                                .build())
                            .putBoolean("search_inventory", true)
                            .putBoolean("use_in_creative", false)
                            .putBoolean("use_offhand", true)
                            .build()
                    ))
                    .putBoolean("charge_on_draw", true)
                    .putFloat("max_draw_duration", 0.0F)
                    .putBoolean("scale_power_by_draw_duration", true)
                    .build());
            }
            case "minecraft:trident" -> itemProperties.putInt("use_animation", BEDROCK_ANIMATIONS.get(Consumable.ItemUseAnimation.SPEAR));
            case "minecraft:crossbow" -> {
                itemProperties.putInt("frame_count", 10);

                componentBuilder.putCompound("minecraft:shooter", NbtMap.builder()
                    .putList("ammunition", NbtType.COMPOUND, List.of(
                        NbtMap.builder()
                            .putCompound("item", NbtMap.builder()
                                .putString("name", "minecraft:arrow")
                                .build())
                            .putBoolean("use_offhand", true)
                            .putBoolean("use_in_creative", false)
                            .putBoolean("search_inventory", true)
                            .build()
                    ))
                    .putBoolean("charge_on_draw", true)
                    .putFloat("max_draw_duration", 1.0F)
                    .putBoolean("scale_power_by_draw_duration", true)
                    .build());
            }
        }
    }

    private static void computeConsumableProperties(Consumable consumable, @Nullable FoodProperties foodProperties, NbtMapBuilder itemProperties, NbtMapBuilder componentBuilder) {
        // this is the duration of the use animation in ticks; note that in behavior packs this is set as a float in seconds, but over the network it is an int in ticks
        itemProperties.putInt("use_duration", (int) (consumable.consumeSeconds() * 20));

        Integer animationId = BEDROCK_ANIMATIONS.get(consumable.animation());
        if (animationId != null) {
            itemProperties.putInt("use_animation", animationId);
            componentBuilder.putCompound("minecraft:use_animation", NbtMap.builder()
                .putString("value", consumable.animation().toString().toLowerCase())
                .build());
        }

        int nutrition = foodProperties == null ? 0 : foodProperties.getNutrition();
        float saturationModifier = foodProperties == null ? 0.0F : foodProperties.getSaturationModifier();
        boolean canAlwaysEat = foodProperties == null || foodProperties.isCanAlwaysEat();
        componentBuilder.putCompound("minecraft:food", NbtMap.builder()
            .putBoolean("can_always_eat", canAlwaysEat)
            .putInt("nutrition", nutrition)
            .putFloat("saturation_modifier", saturationModifier)
            .putCompound("using_converts_to", NbtMap.EMPTY)
            .build());

        componentBuilder.putCompound("minecraft:use_modifiers", NbtMap.builder()
            .putFloat("movement_modifier", 0.35F)
            .putFloat("use_duration", consumable.consumeSeconds())
            .build());
    }

    private static void computeEntityPlacerProperties(NbtMapBuilder componentBuilder) {
        // all items registered that place entities should be given this component to prevent double placement
        // it is okay that the entity here does not match the actual one since we control what entity actually spawns
        componentBuilder.putCompound("minecraft:entity_placer", NbtMap.builder()
            .putList("dispense_on", NbtType.STRING)
            .putString("entity", "minecraft:minecart")
            .putList("use_on", NbtType.STRING)
            .build());
    }

    private static void computeThrowableProperties(NbtMapBuilder componentBuilder) {
        // allows item to be thrown when holding down right click (individual presses are required w/o this component)
        componentBuilder.putCompound("minecraft:throwable", NbtMap.builder().putBoolean("do_swing_animation", true).build());

        // this must be set to something for the swing animation to play
        // it is okay that the projectile here does not match the actual one since we control what entity actually spawns
        componentBuilder.putCompound("minecraft:projectile", NbtMap.builder().putString("projectile_entity", "minecraft:snowball").build());
    }

    private static void computeUseCooldownProperties(UseCooldown cooldown, NbtMapBuilder componentBuilder) {
        Objects.requireNonNull(cooldown.cooldownGroup(), "Cooldown group can't be null");
        componentBuilder.putCompound("minecraft:cooldown", NbtMap.builder()
            .putString("category", cooldown.cooldownGroup().asString())
            .putFloat("duration", cooldown.seconds())
            .build()
        );
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

    private static NbtMap xyzToScaleList(float x, float y, float z) {
        return NbtMap.builder().putList("scale", NbtType.FLOAT, List.of(x, y, z)).build();
    }

    private static boolean isUnbreakableItem(CustomItemDefinition definition) {
        for (CustomItemPredicate predicate : definition.predicates()) {
            if (predicate instanceof ConditionPredicate<?> condition && condition.property() == ConditionPredicateProperty.HAS_COMPONENT && condition.expected()) {
                Identifier component = (Identifier) condition.data();
                if (UNBREAKABLE_COMPONENT.equals(component)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Converts the API components to MCPL ones using the converters in {@link ComponentConverters}, and applies these on top of the default item components.
     *
     * <p>Note that not every API component has a converter in {@link ComponentConverters}. See the documentation there.</p>
     *
     * @see ComponentConverters
     */
    private static DataComponents patchDataComponents(Item javaItem, CustomItemDefinition definition) {
        DataComponents convertedComponents = new DataComponents(new HashMap<>());
        ComponentConverters.convertAndPutComponents(convertedComponents, definition.components());
        return javaItem.gatherComponents(convertedComponents);
    }

    @SuppressWarnings("unchecked")
    private static void addItemTag(NbtMapBuilder builder, Identifier tag) {
        List<String> tagList = (List<String>) builder.get("item_tags");
        if (tagList == null) {
            builder.putList("item_tags", NbtType.STRING, tag.toString());
        } else {
            // NbtList is immutable
            if (!tagList.contains(tag.toString())) {
                tagList = new ArrayList<>(tagList);
                tagList.add(tag.toString());
                builder.putList("item_tags", NbtType.STRING, tagList);
            }
        }
    }
}
