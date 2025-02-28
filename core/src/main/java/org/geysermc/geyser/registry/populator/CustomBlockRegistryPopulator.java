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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.PlacementConditions;
import org.geysermc.geyser.api.block.custom.component.PlacementConditions.Face;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.block.custom.property.CustomBlockProperty;
import org.geysermc.geyser.api.block.custom.property.PropertyType;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.GeyserCustomBlockComponents;
import org.geysermc.geyser.level.block.GeyserCustomBlockData;
import org.geysermc.geyser.level.block.GeyserCustomBlockState;
import org.geysermc.geyser.level.block.GeyserGeometryComponent;
import org.geysermc.geyser.level.block.GeyserMaterialInstance;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.level.physics.PistonBehavior;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.mappings.MappingsConfigReader;
import org.geysermc.geyser.registry.type.CustomSkull;
import org.geysermc.geyser.translator.collision.OtherCollision;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.geysermc.geyser.registry.populator.BlockRegistryPopulator.JAVA_BLOCKS_SIZE;
import static org.geysermc.geyser.registry.populator.BlockRegistryPopulator.MIN_CUSTOM_RUNTIME_ID;

public class CustomBlockRegistryPopulator {

    // Since 1.20.60, custom blocks need a block_id in their nbt tag
    public static AtomicInteger BLOCK_ID = new AtomicInteger();

    // Custom block id's start at 10000, and count up
    public static final int START_OFFSET = 10000;

    /**
     * The stage of population
     */
    public enum Stage {
        DEFINITION,
        VANILLA_REGISTRATION,
        NON_VANILLA_REGISTRATION,
        CUSTOM_REGISTRATION
    }

    /**
     * Populates the custom block registries by stage
     * 
     * @param stage the stage to populate
     */
    public static void populate(Stage stage) {
        if (!GeyserImpl.getInstance().config().addNonBedrockItems()) {
            return;
        }
        
        switch (stage) {
            case DEFINITION -> populateBedrock();
            case VANILLA_REGISTRATION -> populateVanilla();
            case NON_VANILLA_REGISTRATION -> populateNonVanilla();
            case CUSTOM_REGISTRATION -> registration();
            default -> throw new IllegalArgumentException("Unknown stage: " + stage);
        }
    }

    private static Set<CustomBlockData> CUSTOM_BLOCKS;
    private static Map<String, CustomBlockData> CUSTOM_BLOCK_ITEM_OVERRIDES;
    private static Map<JavaBlockState, CustomBlockState> NON_VANILLA_BLOCK_STATE_OVERRIDES;
    private static Map<String, CustomBlockState> BLOCK_STATE_OVERRIDES_QUEUE;

    /**
     * Initializes custom blocks defined by API
     */
    private static void populateBedrock() {
        CUSTOM_BLOCKS = new ObjectOpenHashSet<>();
        CUSTOM_BLOCK_ITEM_OVERRIDES = new HashMap<>();
        NON_VANILLA_BLOCK_STATE_OVERRIDES = new HashMap<>();
        BLOCK_STATE_OVERRIDES_QUEUE = new HashMap<>();

        Set<String> customBlockIdentifiers = new ObjectOpenHashSet<>();
        GeyserImpl.getInstance().getEventBus().fire(new GeyserDefineCustomBlocksEvent() {
            @Override
            public void register(@NonNull CustomBlockData customBlockData) {
                if (customBlockData.name().isEmpty()) {
                    throw new IllegalArgumentException("Custom block name must have at least 1 character.");
                }
                if (!customBlockIdentifiers.add(customBlockData.identifier())) {
                    throw new IllegalArgumentException("Another custom block was already registered under the identifier: " + customBlockData.identifier());
                }
                if (Character.isDigit(customBlockData.name().charAt(0))) {
                    throw new IllegalArgumentException("Custom block can not start with a digit. Name: " + customBlockData.name());
                }
                CUSTOM_BLOCKS.add(customBlockData);
            }

            @Override
            public void registerOverride(@NonNull String javaIdentifier, @NonNull CustomBlockState customBlockState) {
                if (!CUSTOM_BLOCKS.contains(customBlockState.block())) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockState.name());
                }
                // We can't register these yet as we don't have the java block id registry populated
                BLOCK_STATE_OVERRIDES_QUEUE.put(javaIdentifier, customBlockState);
            }

            @Override
            public void registerItemOverride(@NonNull String javaIdentifier, @NonNull CustomBlockData customBlockData) {
                if (!CUSTOM_BLOCKS.contains(customBlockData)) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockData.name());
                }
                CUSTOM_BLOCK_ITEM_OVERRIDES.put(javaIdentifier, customBlockData);
            }

            @Override
            public void registerOverride(@NonNull JavaBlockState javaBlockState, @NonNull CustomBlockState customBlockState) {
                if (!CUSTOM_BLOCKS.contains(customBlockState.block())) {
                    throw new IllegalArgumentException("Custom block is unregistered. Name: " + customBlockState.name());
                }
                NON_VANILLA_BLOCK_STATE_OVERRIDES.put(javaBlockState, customBlockState);
            }
        });
    }

    /**
     * Registers all vanilla custom blocks and skulls defined by API and mappings
     */
    private static void populateVanilla() {
        Int2ObjectMap<CustomBlockState> blockStateOverrides = new Int2ObjectOpenHashMap<>();

        for (CustomSkull customSkull : BlockRegistries.CUSTOM_SKULLS.get().values()) {
            CUSTOM_BLOCKS.add(customSkull.getCustomBlockData());
        }

        for(Map.Entry<String, CustomBlockState> entry : BLOCK_STATE_OVERRIDES_QUEUE.entrySet()) {
            int id = BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(entry.getKey(), -1);
            if (id == -1) {
                GeyserImpl.getInstance().getLogger().warning("Custom block state override for Java Identifier: " +
                        entry.getKey() + " could not be registered as it is not a valid block state.");
                continue;
            }

            CustomBlockState oldBlockState = blockStateOverrides.put(id, entry.getValue());
            if (oldBlockState != null) {
                GeyserImpl.getInstance().getLogger().warning("Duplicate block state override for Java Identifier: " +
                        entry.getKey() + " Old override: " + oldBlockState.name() + " New override: " + entry.getValue().name());
            }
        }
        BLOCK_STATE_OVERRIDES_QUEUE = null;

        Map<CustomBlockData, Set<Integer>> extendedCollisionBoxes = new HashMap<>();
        Map<BoxComponent, CustomBlockData> extendedCollisionBoxSet = new HashMap<>();
        MappingsConfigReader mappingsConfigReader = new MappingsConfigReader();
        mappingsConfigReader.loadBlockMappingsFromJson((key, block) -> {
            CUSTOM_BLOCKS.add(block.data());
            if (block.overrideItem()) {
                CUSTOM_BLOCK_ITEM_OVERRIDES.put(block.javaIdentifier(), block.data());
            }
            block.states().forEach((javaIdentifier, customBlockState) -> {
                int id = BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(javaIdentifier, -1);
                blockStateOverrides.put(id, customBlockState.state());
                BoxComponent extendedCollisionBox = customBlockState.extendedCollisionBox();
                if (extendedCollisionBox != null) {
                    CustomBlockData extendedCollisionBlock = extendedCollisionBoxSet.computeIfAbsent(extendedCollisionBox, box -> {
                        CustomBlockData collisionBlock = createExtendedCollisionBlock(box, extendedCollisionBoxSet.size());
                        CUSTOM_BLOCKS.add(collisionBlock);
                        return collisionBlock;
                    });
                    extendedCollisionBoxes.computeIfAbsent(extendedCollisionBlock, k -> new HashSet<>())
                            .add(id);
                }
            });
        });
    
        BlockRegistries.CUSTOM_BLOCK_STATE_OVERRIDES.set(blockStateOverrides);
        if (!blockStateOverrides.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + blockStateOverrides.size() + " custom block overrides.");
        }

        BlockRegistries.CUSTOM_BLOCK_ITEM_OVERRIDES.set(CUSTOM_BLOCK_ITEM_OVERRIDES);
        if (!CUSTOM_BLOCK_ITEM_OVERRIDES.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + CUSTOM_BLOCK_ITEM_OVERRIDES.size() + " custom block item overrides.");
        }

        BlockRegistries.EXTENDED_COLLISION_BOXES.set(extendedCollisionBoxes);
        if (!extendedCollisionBoxes.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + extendedCollisionBoxes.size() + " custom block extended collision boxes.");
        }
    }

    /**
     * Registers all non-vanilla custom blocks defined by API
     */
    private static void populateNonVanilla() {
        BlockRegistries.NON_VANILLA_BLOCK_STATE_OVERRIDES.set(NON_VANILLA_BLOCK_STATE_OVERRIDES);

        if (NON_VANILLA_BLOCK_STATE_OVERRIDES.isEmpty()) {
            // Nothing left to register, freeze block state registry
            BlockRegistries.BLOCK_STATES.freeze();
            return;
        }

        MIN_CUSTOM_RUNTIME_ID = BlockRegistries.NON_VANILLA_BLOCK_STATE_OVERRIDES.get().keySet().stream().min(Comparator.comparing(JavaBlockState::javaId)).orElseThrow().javaId();
        int maxCustomRuntimeID = BlockRegistries.NON_VANILLA_BLOCK_STATE_OVERRIDES.get().keySet().stream().max(Comparator.comparing(JavaBlockState::javaId)).orElseThrow().javaId();

        if (MIN_CUSTOM_RUNTIME_ID < BlockRegistries.BLOCK_STATES.get().size()) {
            throw new RuntimeException("Non vanilla custom block state overrides runtime ID must start after the last vanilla block state (" + JAVA_BLOCKS_SIZE + ")");
        }

        JAVA_BLOCKS_SIZE = maxCustomRuntimeID + 1; // Runtime ids start at 0, so we need to add 1

        // Now: Vanilla blocks are already loaded and registered; let's load non-vanilla properly too
        IntSet usedNonVanillaRuntimeIDs = new IntOpenHashSet();

        for (JavaBlockState javaBlockState : BlockRegistries.NON_VANILLA_BLOCK_STATE_OVERRIDES.get().keySet()) {
            if (!usedNonVanillaRuntimeIDs.add(javaBlockState.javaId())) {
                throw new RuntimeException("Duplicate runtime ID " + javaBlockState.javaId() + " for non vanilla Java block state " + javaBlockState.identifier());
            }

            String javaId = javaBlockState.identifier();
            int stateRuntimeId = javaBlockState.javaId();
            String pistonBehavior = javaBlockState.pistonBehavior();

            Block.Builder builder = Block.builder()
                .javaId(stateRuntimeId)
                .destroyTime(javaBlockState.blockHardness())
                .pushReaction(pistonBehavior == null ? PistonBehavior.NORMAL : PistonBehavior.getByName(pistonBehavior));
            if (!javaBlockState.canBreakWithHand()) {
                builder.requiresCorrectToolForDrops();
            }
            String cleanJavaIdentifier = BlockUtils.getCleanIdentifier(javaBlockState.identifier());
            String pickItem = javaBlockState.pickItem();
            Block block = new Block(cleanJavaIdentifier, builder) {
                @Override
                public ItemStack pickItem(BlockState state) {
                    if (this.item == null) {
                        this.item = Registries.JAVA_ITEM_IDENTIFIERS.get(pickItem);
                        if (this.item == null) {
                            GeyserImpl.getInstance().getLogger().warning("We could not find item " + pickItem
                                + " for getting the item for block " + javaBlockState.identifier());
                            this.item = Items.AIR;
                        }
                    }
                    return new ItemStack(this.item.javaId());
                }
            };
            block.setJavaId(javaBlockState.stateGroupId());

            BlockRegistries.JAVA_BLOCKS.registerWithAnyIndex(javaBlockState.stateGroupId(), block, Blocks.AIR);
            BlockRegistries.JAVA_IDENTIFIER_TO_ID.register(javaId, stateRuntimeId);

            // TODO register different collision types?
            BoundingBox[] geyserCollisions = Arrays.stream(javaBlockState.collision())
                .map(box -> new BoundingBox(box.middleX(), box.middleY(), box.middleZ(),
                    box.sizeX(), box.sizeY(), box.sizeZ()))
                .toArray(BoundingBox[]::new);
            OtherCollision collision = new OtherCollision(geyserCollisions);
            BlockRegistries.COLLISIONS.registerWithAnyIndex(javaBlockState.javaId(), collision, collision);
        }

        BlockRegistries.BLOCK_STATES.freeze();

        if (!NON_VANILLA_BLOCK_STATE_OVERRIDES.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + NON_VANILLA_BLOCK_STATE_OVERRIDES.size() + " non-vanilla block overrides.");
        }
    }

    /**
     * Registers all bedrock custom blocks defined in previous stages
     */
    private static void registration() {
        BlockRegistries.CUSTOM_BLOCKS.set(CUSTOM_BLOCKS.toArray(new CustomBlockData[0]));
        if (!CUSTOM_BLOCKS.isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + CUSTOM_BLOCKS.size() + " custom blocks.");
        }
    }

    /**
     * Generates and appends all custom block states to the provided list of custom block states
     * Appends the custom block states to the provided list of NBT maps
     * 
     * @param customBlock the custom block data to generate states for
     * @param blockStates the list of NBT maps to append the custom block states to
     * @param customExtBlockStates the list of custom block states to append the custom block states to
     */
    static void generateCustomBlockStates(CustomBlockData customBlock, List<NbtMap> blockStates, List<CustomBlockState> customExtBlockStates) {
        int totalPermutations = 1;
        for (CustomBlockProperty<?> property : customBlock.properties().values()) {
            totalPermutations *= property.values().size();
        }
    
        for (int i = 0; i < totalPermutations; i++) {
            NbtMapBuilder statesBuilder = NbtMap.builder();
            int permIndex = i;
            for (CustomBlockProperty<?> property : customBlock.properties().values()) {
                statesBuilder.put(property.name(), property.values().get(permIndex % property.values().size()));
                permIndex /= property.values().size();
            }
            NbtMap states = statesBuilder.build();
    
            blockStates.add(NbtMap.builder()
                    .putString("name", customBlock.identifier())
                    .putCompound("states", states)
                    .build());
            customExtBlockStates.add(new GeyserCustomBlockState(customBlock, states));
        }
    }

    /**
     * Generates and returns the block property data for the provided custom block
     * 
     * @param customBlock the custom block to generate block property data for
     * @param protocolVersion the protocol version to use for the block property data
     * @return the block property data for the provided custom block
     */
    @SuppressWarnings("unchecked")
    static BlockPropertyData generateBlockPropertyData(CustomBlockData customBlock, int protocolVersion) {
        List<NbtMap> permutations = new ArrayList<>();
        for (CustomBlockPermutation permutation : customBlock.permutations()) {
            permutations.add(NbtMap.builder()
                    .putCompound("components", CustomBlockRegistryPopulator.convertComponents(permutation.components(), protocolVersion))
                    .putString("condition", permutation.condition())
                    .build());
        }
    
        // The order that properties are defined influences the order that block states are generated
        List<NbtMap> properties = new ArrayList<>();
        for (CustomBlockProperty<?> property : customBlock.properties().values()) {
            NbtMapBuilder propertyBuilder = NbtMap.builder()
                    .putString("name", property.name());
            if (property.type() == PropertyType.booleanProp()) {
                propertyBuilder.putList("enum", NbtType.BYTE, List.of((byte) 0, (byte) 1));
            } else if (property.type() == PropertyType.integerProp()) {
                propertyBuilder.putList("enum", NbtType.INT, (List<Integer>) property.values());
            } else if (property.type() == PropertyType.stringProp()) {
                propertyBuilder.putList("enum", NbtType.STRING, (List<String>) property.values());
            }
            properties.add(propertyBuilder.build());
        }
    
        CreativeCategory creativeCategory = customBlock.creativeCategory() != null ? customBlock.creativeCategory() : CreativeCategory.NONE;
        String creativeGroup = customBlock.creativeGroup() != null ? customBlock.creativeGroup() : "";
        NbtMapBuilder propertyTag = NbtMap.builder()
                .putCompound("components", CustomBlockRegistryPopulator.convertComponents(customBlock.components(), protocolVersion))
                // this is required or the client will crash
                // in the future, this can be used to replace items in the creative inventory
                // this would require us to map https://wiki.bedrock.dev/documentation/creative-categories.html#for-blocks programatically
                .putCompound("menu_category", NbtMap.builder()
                    .putString("category", creativeCategory.internalName())
                    .putString("group", creativeGroup)
                    .putBoolean("is_hidden_in_commands", false)
                .build())
                // meaning of this version is unknown, but it's required for tags to work and should probably be checked periodically
                .putInt("molangVersion", 1)
                .putList("permutations", NbtType.COMPOUND, permutations)
                .putList("properties", NbtType.COMPOUND, properties)
                .putCompound("vanilla_block_data", NbtMap.builder()
                    .putInt("block_id", BLOCK_ID.getAndIncrement())
                    .build());

        return new BlockPropertyData(customBlock.identifier(), propertyTag.build());
    }

    /**
     * Converts the provided custom block components to an {@link NbtMap} to be sent to the client in the StartGame packet
     * 
     * @param components the custom block components to convert
     * @param protocolVersion the protocol version to use for the conversion
     * @return the NBT representation of the provided custom block components
     */
    private static NbtMap convertComponents(CustomBlockComponents components, int protocolVersion) {
        if (components == null) {
            return NbtMap.EMPTY;
        }

        NbtMapBuilder builder = NbtMap.builder();
        if (components.displayName() != null) {
            builder.putCompound("minecraft:display_name", NbtMap.builder()
                    .putString("value", components.displayName())
                    .build());
        }

        if (components.selectionBox() != null) {
            builder.putCompound("minecraft:selection_box", convertBox(components.selectionBox()));
        }

        if (components.collisionBox() != null) {
            builder.putCompound("minecraft:collision_box", convertBox(components.collisionBox()));
        }

        if (components.geometry() != null) {
            NbtMapBuilder geometryBuilder = NbtMap.builder();
            if (protocolVersion >= Bedrock_v594.CODEC.getProtocolVersion()) {
                geometryBuilder.putString("identifier", components.geometry().identifier());
                if (components.geometry().boneVisibility() != null) {
                    NbtMapBuilder boneVisibilityBuilder = NbtMap.builder();
                    components.geometry().boneVisibility().entrySet().forEach(
                        entry -> boneVisibilityBuilder.putString(entry.getKey(), entry.getValue()));
                    geometryBuilder.putCompound("bone_visibility", boneVisibilityBuilder.build());
                }
            } else {
                geometryBuilder.putString("value", components.geometry().identifier());
            }
            builder.putCompound("minecraft:geometry", geometryBuilder.build());
        }

        if (!components.materialInstances().isEmpty()) {
            NbtMapBuilder materialsBuilder = NbtMap.builder();
            for (Map.Entry<String, MaterialInstance> entry : components.materialInstances().entrySet()) {
                MaterialInstance materialInstance = entry.getValue();
                NbtMapBuilder materialBuilder = NbtMap.builder()
                        .putString("render_method", materialInstance.renderMethod())
                        .putBoolean("face_dimming", materialInstance.faceDimming())
                        .putBoolean("ambient_occlusion", materialInstance.faceDimming());
                // Texture can be unspecified when blocks.json is used in RP (https://wiki.bedrock.dev/blocks/blocks-stable.html#minecraft-material-instances)
                if (materialInstance.texture() != null) {
                    materialBuilder.putString("texture", materialInstance.texture());
                }
                materialsBuilder.putCompound(entry.getKey(), materialBuilder.build());
            }

            builder.putCompound("minecraft:material_instances", NbtMap.builder()
                    // we could read these, but there is no functional reason to use them at the moment
                    // they only allow you to make aliases for material instances
                    // but you could already just define the same instance twice if this was really needed
                    .putCompound("mappings", NbtMap.EMPTY)
                    .putCompound("materials", materialsBuilder.build())
                    .build());
        }

        if (components.placementFilter() != null) {
            builder.putCompound("minecraft:placement_filter", NbtMap.builder()
                    .putList("conditions", NbtType.COMPOUND, convertPlacementFilter(components.placementFilter()))
                    .build());
        }

        if (components.destructibleByMining() != null) {
            builder.putCompound("minecraft:destructible_by_mining", NbtMap.builder()
                    .putFloat("value", components.destructibleByMining())
                    .build());
        }

        if (components.friction() != null) {
            builder.putCompound("minecraft:friction", NbtMap.builder()
                    .putFloat("value", components.friction())
                    .build());
        }

        if (components.lightEmission() != null) {
            builder.putCompound("minecraft:light_emission", NbtMap.builder()
                    .putByte("emission", components.lightEmission().byteValue())
                    .build());
        }

        if (components.lightDampening() != null) {
            builder.putCompound("minecraft:light_dampening", NbtMap.builder()
                    .putByte("lightLevel", components.lightDampening().byteValue())
                    .build());
        }

        if (components.transformation() != null) {
            builder.putCompound("minecraft:transformation", NbtMap.builder()
                    .putInt("RX", MathUtils.unwrapDegreesToInt(components.transformation().rx()) / 90)
                    .putInt("RY", MathUtils.unwrapDegreesToInt(components.transformation().ry()) / 90)
                    .putInt("RZ", MathUtils.unwrapDegreesToInt(components.transformation().rz()) / 90)
                    .putFloat("SX", components.transformation().sx())
                    .putFloat("SY", components.transformation().sy())
                    .putFloat("SZ", components.transformation().sz())
                    .putFloat("TX", components.transformation().tx())
                    .putFloat("TY", components.transformation().ty())
                    .putFloat("TZ", components.transformation().tz())
                    .build());
        }

        // place_air is not an actual component
        // We just apply a dummy event to prevent the client from trying to place a block
        // This mitigates the issue with the client sometimes double placing blocks
        if (components.placeAir()) {
            builder.putCompound("minecraft:on_player_placing", NbtMap.builder()
                    .putString("triggerType", "geyser:place_event")
                    .build());
        }

        if (!components.tags().isEmpty()) {
            components.tags().forEach(tag -> builder.putCompound("tag:" + tag, NbtMap.EMPTY));
        }

        return builder.build();
    }

    /**
     * Converts the provided box component to an {@link NbtMap}
     * 
     * @param boxComponent the box component to convert
     * @return the NBT representation of the provided box component
     */
    private static NbtMap convertBox(BoxComponent boxComponent) {
        return NbtMap.builder()
                .putBoolean("enabled", !boxComponent.isEmpty())
                .putList("origin", NbtType.FLOAT, boxComponent.originX(), boxComponent.originY(), boxComponent.originZ())
                .putList("size", NbtType.FLOAT, boxComponent.sizeX(), boxComponent.sizeY(), boxComponent.sizeZ())
                .build();
    }

    /**
     * Converts the provided placement filter to a list of {@link NbtMap}
     * 
     * @param placementFilter the placement filter to convert
     * @return the NBT representation of the provided placement filter
     */
    private static List<NbtMap> convertPlacementFilter(List<PlacementConditions> placementFilter) {
        List<NbtMap> conditions = new ArrayList<>();
        placementFilter.forEach((condition) -> {
            NbtMapBuilder conditionBuilder = NbtMap.builder();

            // allowed_faces on the network is represented by 6 bits for the 6 possible faces
            // the enum has the proper values for that face only, so we just bitwise OR them together
            byte allowedFaces = 0;
            for (Face face : condition.allowedFaces()) { allowedFaces |= (1 << face.ordinal()); }
            conditionBuilder.putByte("allowed_faces", allowedFaces);

            // block_filters is a list of either blocks or queries for block tags
            // if these match the block the player is trying to place on, the placement is allowed by the client
            List <NbtMap> blockFilters = new ArrayList<>();
            condition.blockFilters().forEach((value, type) -> {
                NbtMapBuilder blockFilterBuilder = NbtMap.builder();
                switch (type) {
                    case BLOCK -> blockFilterBuilder.putString("name", value);
                    // meaning of this version is unknown, but it's required for tags to work and should probably be checked periodically
                    case TAG -> blockFilterBuilder.putString("tags", value).putInt("tags_version", 6);
                }
                blockFilters.add(blockFilterBuilder.build());
            });
            conditionBuilder.putList("block_filters", NbtType.COMPOUND, blockFilters);
            conditions.add(conditionBuilder.build());
        });

        return conditions;
    }

    private static CustomBlockData createExtendedCollisionBlock(BoxComponent boxComponent, int extendedCollisionBlock) {
        return new GeyserCustomBlockData.Builder()
                .name("extended_collision_" + extendedCollisionBlock)
                .components(
                    new GeyserCustomBlockComponents.Builder()
                        .collisionBox(boxComponent)
                        .selectionBox(BoxComponent.emptyBox())
                        .materialInstance("*", new GeyserMaterialInstance.Builder()
                            .texture("glass")
                            .renderMethod("alpha_test")
                            .faceDimming(false)
                            .ambientOcclusion(false)
                            .build())
                        .lightDampening(0)
                        .geometry(new GeyserGeometryComponent.Builder()
                            .identifier("geometry.invisible")
                            .build())
                        .build())
                .build();
    }
}
