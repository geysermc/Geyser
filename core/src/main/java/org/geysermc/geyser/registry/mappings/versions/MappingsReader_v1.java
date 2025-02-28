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

package org.geysermc.geyser.registry.mappings.versions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.PlacementConditions;
import org.geysermc.geyser.api.block.custom.component.PlacementConditions.BlockFilterType;
import org.geysermc.geyser.api.block.custom.component.PlacementConditions.Face;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.level.block.GeyserCustomBlockComponents;
import org.geysermc.geyser.level.block.GeyserCustomBlockData;
import org.geysermc.geyser.level.block.GeyserGeometryComponent;
import org.geysermc.geyser.level.block.GeyserMaterialInstance;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.mappings.util.CustomBlockComponentsMapping;
import org.geysermc.geyser.registry.mappings.util.CustomBlockMapping;
import org.geysermc.geyser.registry.mappings.util.CustomBlockStateBuilderMapping;
import org.geysermc.geyser.registry.mappings.util.CustomBlockStateMapping;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.geyser.util.MinecraftKey;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A class responsible for reading custom item and block mappings from a JSON file
 */
public class MappingsReader_v1 extends MappingsReader {
    @Override
    public void readItemMappings(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomItemData> consumer) {
        this.readItemMappingsV1(file, mappingsRoot, consumer);
    }

    /**
     * Read item block from a JSON node
     * 
     * @param file The path to the file
     * @param mappingsRoot The {@link JsonObject} containing the mappings
     * @param consumer The consumer to accept the mappings
     * @see #readBlockMappingsV1(Path, JsonObject, BiConsumer)
     */
    @Override
    public void readBlockMappings(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomBlockMapping> consumer) {
        this.readBlockMappingsV1(file, mappingsRoot, consumer);
    }

    public void readItemMappingsV1(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomItemData> consumer) {
        JsonObject itemsNode = mappingsRoot.getAsJsonObject("items");

        if (itemsNode != null) {
            itemsNode.entrySet().forEach(entry -> {
                if (entry.getValue() instanceof JsonArray array) {
                    array.forEach(data -> {
                        try {
                            CustomItemData customItemData = this.readItemMappingEntry((JsonObject) data);
                            consumer.accept(entry.getKey(), customItemData);
                        } catch (InvalidCustomMappingsFileException e) {
                            GeyserImpl.getInstance().getLogger().error("Error in registering items for custom mapping file: " + file.toString(), e);
                        }
                    });
                }
            });
        }
    }

    /**
     * Read block mappings from a JSON node
     * 
     * @param file The path to the file
     * @param mappingsRoot The {@link JsonObject} containing the mappings
     * @param consumer The consumer to accept the mappings
     * @see #readBlockMappings(Path, JsonObject, BiConsumer)
     */
    public void readBlockMappingsV1(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomBlockMapping> consumer) {
        if (mappingsRoot.get("blocks") instanceof JsonObject blocksNode) {
            blocksNode.entrySet().forEach(entry -> {
                if (entry.getValue() instanceof JsonObject jsonObject) {
                    try {
                        String identifier = MinecraftKey.key(entry.getKey()).asString();
                        CustomBlockMapping customBlockMapping = this.readBlockMappingEntry(identifier, jsonObject);
                        consumer.accept(identifier, customBlockMapping);
                    } catch (Exception e) {
                        GeyserImpl.getInstance().getLogger().error("Error in registering blocks for custom mapping file: " + file.toString());
                        GeyserImpl.getInstance().getLogger().error("due to entry: " + entry, e);
                    }
                }
            });
        }
    }

    private CustomItemOptions readItemCustomItemOptions(JsonObject node) {
        CustomItemOptions.Builder customItemOptions = CustomItemOptions.builder();

        JsonElement customModelData = node.get("custom_model_data");
        if (customModelData != null && customModelData.isJsonPrimitive()) {
            customItemOptions.customModelData(customModelData.getAsInt());
        }

        JsonElement damagePredicate = node.get("damage_predicate");
        if (damagePredicate != null && damagePredicate.isJsonPrimitive()) {
            customItemOptions.damagePredicate(damagePredicate.getAsInt());
        }

        JsonElement unbreakable = node.get("unbreakable");
        if (unbreakable != null && unbreakable.isJsonPrimitive()) {
            customItemOptions.unbreakable(unbreakable.getAsBoolean());
        }

        JsonElement defaultItem = node.get("default");
        if (defaultItem != null && defaultItem.isJsonPrimitive()) {
            customItemOptions.defaultItem(defaultItem.getAsBoolean());
        }

        return customItemOptions.build();
    }

    @Override
    public CustomItemData readItemMappingEntry(JsonObject node) throws InvalidCustomMappingsFileException {
        if (node == null) {
            throw new InvalidCustomMappingsFileException("Invalid item mappings entry");
        }

        JsonElement name = node.get("name");
        if (name == null || !name.isJsonPrimitive() || name.getAsString().isEmpty()) {
            throw new InvalidCustomMappingsFileException("An item entry has no name");
        }

        CustomItemData.Builder customItemData = CustomItemData.builder()
                .name(name.getAsString())
                .customItemOptions(this.readItemCustomItemOptions(node));

        //The next entries are optional
        if (node.has("display_name")) {
            customItemData.displayName(node.get("display_name").getAsString());
        }

        if (node.has("icon")) {
            customItemData.icon(node.get("icon").getAsString());
        }

        if (node.has("creative_category")) {
            customItemData.creativeCategory(node.get("creative_category").getAsInt());
        }

        if (node.has("creative_group")) {
            customItemData.creativeGroup(node.get("creative_group").getAsString());
        }

        if (node.has("allow_offhand")) {
            customItemData.allowOffhand(node.get("allow_offhand").getAsBoolean());
        }

        if (node.has("display_handheld")) {
            customItemData.displayHandheld(node.get("display_handheld").getAsBoolean());
        }

        if (node.has("texture_size")) {
            customItemData.textureSize(node.get("texture_size").getAsInt());
        }

        if (node.has("render_offsets")) {
            JsonObject tmpNode = node.getAsJsonObject("render_offsets");

            customItemData.renderOffsets(fromJsonObject(tmpNode));
        }

        if (node.get("tags") instanceof JsonArray tags) {
            Set<String> tagsSet = new ObjectOpenHashSet<>();
            tags.forEach(tag -> tagsSet.add(tag.getAsString()));
            customItemData.tags(tagsSet);
        }

        return customItemData.build();
    }

    /**
     * Read a block mapping entry from a JSON node and Java identifier
     * 
     * @param identifier The Java identifier of the block
     * @param node The {@link JsonObject} containing the block mapping entry
     * @return The {@link CustomBlockMapping} record to be read by {@link org.geysermc.geyser.registry.populator.CustomBlockRegistryPopulator}
     * @throws InvalidCustomMappingsFileException If the JSON node is invalid
     */
    @Override
    public CustomBlockMapping readBlockMappingEntry(String identifier, JsonObject node) throws InvalidCustomMappingsFileException {
        if (node == null) {
            throw new InvalidCustomMappingsFileException("Invalid block mappings entry:" + node);
        }

        String name = node.get("name").getAsString();
        if (name == null || name.isEmpty()) {
            throw new InvalidCustomMappingsFileException("A block entry has no name");
        }

        boolean includedInCreativeInventory = node.has("included_in_creative_inventory") && node.get("included_in_creative_inventory").getAsBoolean();

        CreativeCategory creativeCategory = CreativeCategory.NONE;
        if (node.has("creative_category")) {
            String categoryName = node.get("creative_category").getAsString();
            try {
                creativeCategory = CreativeCategory.valueOf(categoryName.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidCustomMappingsFileException("Invalid creative category \"" + categoryName + "\" for block \"" + name + "\"");
            }
        }

        String creativeGroup = "";
        if (node.has("creative_group")) {
            creativeGroup = node.get("creative_group").getAsString();
        }

        // If this is true, we will only register the states the user has specified rather than all the possible block states
        boolean onlyOverrideStates = node.has("only_override_states") && node.get("only_override_states").getAsBoolean();

        // Create the data for the overall block
        CustomBlockData.Builder customBlockDataBuilder = new GeyserCustomBlockData.Builder()
                .name(name)
                .includedInCreativeInventory(includedInCreativeInventory)
                .creativeCategory(creativeCategory)
                .creativeGroup(creativeGroup);

        if (BlockRegistries.JAVA_IDENTIFIER_TO_ID.get().containsKey(identifier)) {
            // There is only one Java block state to override
            CustomBlockComponentsMapping componentsMapping = createCustomBlockComponentsMapping(node, identifier, name);
            CustomBlockData blockData = customBlockDataBuilder
                    .components(componentsMapping.components())
                    .build();
            return new CustomBlockMapping(blockData, Map.of(identifier, new CustomBlockStateMapping(blockData.defaultBlockState(), componentsMapping.extendedCollisionBox())), identifier, !onlyOverrideStates);
        }

        Map<String, CustomBlockComponentsMapping> componentsMap = new LinkedHashMap<>();

        if (node.get("state_overrides") instanceof JsonObject stateOverrides) {
            // Load components for specific Java block states
            for (Map.Entry<String, JsonElement> overrideEntry : stateOverrides.entrySet()) {
                String state = identifier + "[" + overrideEntry.getKey() + "]";
                if (!BlockRegistries.JAVA_IDENTIFIER_TO_ID.get().containsKey(state)) {
                    throw new InvalidCustomMappingsFileException("Unknown Java block state: " + state + " for state_overrides.");
                }
                componentsMap.put(state, createCustomBlockComponentsMapping(overrideEntry.getValue(), state, name));
            }
        }
        if (componentsMap.isEmpty() && onlyOverrideStates) {
            throw new InvalidCustomMappingsFileException("Block entry for " + identifier + " has only_override_states set to true, but has no state_overrides.");
        }

        if (!onlyOverrideStates) {
            // Create components for any remaining Java block states
            BlockRegistries.JAVA_IDENTIFIER_TO_ID.get().keySet()
                    .stream()
                    .filter(s -> s.startsWith(identifier + "["))
                    .filter(Predicate.not(componentsMap::containsKey))
                    .forEach(state -> componentsMap.put(state, createCustomBlockComponentsMapping(null, state, name)));
        }

        if (componentsMap.isEmpty()) {
            throw new InvalidCustomMappingsFileException("Unknown Java block: " + identifier);
        }

        // We pass in the first state and just use the hitbox from that as the default
        // Each state will have its own so this is fine
        String firstState = componentsMap.keySet().iterator().next();
        CustomBlockComponentsMapping firstComponentsMapping = createCustomBlockComponentsMapping(node, firstState, name);
        customBlockDataBuilder.components(firstComponentsMapping.components());

        return createCustomBlockMapping(customBlockDataBuilder, componentsMap, identifier, !onlyOverrideStates);
    }

    private CustomBlockMapping createCustomBlockMapping(CustomBlockData.Builder customBlockDataBuilder, Map<String, CustomBlockComponentsMapping> componentsMap, String identifier, boolean overrideItem) {
        Map<String, LinkedHashSet<String>> valuesMap = new Object2ObjectOpenHashMap<>();

        List<CustomBlockPermutation> permutations = new ArrayList<>();
        Map<String, CustomBlockStateBuilderMapping> blockStateBuilders = new Object2ObjectOpenHashMap<>();

        // For each Java block state, extract the property values, create a CustomBlockPermutation,
        // and a CustomBlockState builder
        for (Map.Entry<String, CustomBlockComponentsMapping> entry : componentsMap.entrySet()) {
            String state = entry.getKey();
            String[] pairs = splitStateString(state);

            String[] conditions = new String[pairs.length];
            Function<CustomBlockState.Builder, CustomBlockState.Builder> blockStateBuilder = Function.identity();

            for (int i = 0; i < pairs.length; i++) {
                String[] parts = pairs[i].split("=");
                String property = parts[0];
                String value = parts[1];

                valuesMap.computeIfAbsent(property, k -> new LinkedHashSet<>())
                        .add(value);

                conditions[i] = String.format("q.block_property('%s') == '%s'", property, value);
                blockStateBuilder = blockStateBuilder.andThen(builder -> builder.stringProperty(property, value));
            }

            permutations.add(new CustomBlockPermutation(entry.getValue().components(), String.join(" && ", conditions)));
            blockStateBuilders.put(state, new CustomBlockStateBuilderMapping(blockStateBuilder.andThen(CustomBlockState.Builder::build), entry.getValue().extendedCollisionBox()));
        }

        valuesMap.forEach((key, value) -> customBlockDataBuilder.stringProperty(key, new ArrayList<>(value)));

        CustomBlockData customBlockData = customBlockDataBuilder
                .permutations(permutations)
                .build();
        // Build CustomBlockStates for each Java block state we wish to override
        Map<String, CustomBlockStateMapping> states = blockStateBuilders.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> new CustomBlockStateMapping(e.getValue().builder().apply(customBlockData.blockStateBuilder()), e.getValue().extendedCollisionBox())));

        return new CustomBlockMapping(customBlockData, states, identifier, overrideItem);
    }

    /**
     * Creates a {@link CustomBlockComponents} object for the passed state override or base block node, Java block state identifier, and custom block name
     * 
     * @param element the state override or base block {@link JsonObject}
     * @param stateKey the Java block state identifier
     * @param name the name of the custom block
     * @return the {@link CustomBlockComponents} object
     */
    private CustomBlockComponentsMapping createCustomBlockComponentsMapping(JsonElement element, String stateKey, String name) {
        // This is needed to find the correct selection box for the given block
        int id = BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(stateKey, -1);
        BoxComponent boxComponent = createBoxComponent(id);
        BoxComponent extendedBoxComponent = createExtendedBoxComponent(id);
        CustomBlockComponents.Builder builder = new GeyserCustomBlockComponents.Builder()
                .collisionBox(boxComponent)
                .selectionBox(boxComponent);

        if (!(element instanceof JsonObject node)) {
            // No other components were defined
            return new CustomBlockComponentsMapping(builder.build(), extendedBoxComponent);
        }

        BoxComponent selectionBox = createBoxComponent(node.get("selection_box"));
        if (selectionBox != null) {
            builder.selectionBox(selectionBox);
        }
        BoxComponent collisionBox = createBoxComponent(node.get("collision_box"));
        if (collisionBox != null) {
            builder.collisionBox(collisionBox);
        }
        BoxComponent extendedCollisionBox = createBoxComponent(node.get("extended_collision_box"));
        if (extendedCollisionBox != null) {
            extendedBoxComponent = extendedCollisionBox;
        }


        // We set this to max value by default so that we may dictate the correct destroy time ourselves
        float destructibleByMining = Float.MAX_VALUE;
        if (node.has("destructible_by_mining")) {
            destructibleByMining = node.get("destructible_by_mining").getAsFloat();
        }
        builder.destructibleByMining(destructibleByMining);

        if (node.has("geometry")) {
            if (node.get("geometry").isJsonPrimitive()) {
                builder.geometry(new GeyserGeometryComponent.Builder()
                        .identifier(node.get("geometry").getAsString())
                        .build());
            } else {
                JsonObject geometry = node.getAsJsonObject("geometry");
                GeometryComponent.Builder geometryBuilder = new GeyserGeometryComponent.Builder();
                if (geometry.has("identifier")) {
                    geometryBuilder.identifier(geometry.get("identifier").getAsString());
                }
                if (geometry.has("bone_visibility")) {
                    if (geometry.get("bone_visibility") instanceof JsonObject boneVisibility) {
                        Map<String, String> boneVisibilityMap = new Object2ObjectOpenHashMap<>();
                        boneVisibility.entrySet().forEach(entry -> {
                            String key = entry.getKey();
                            String value = entry.getValue() instanceof JsonPrimitive primitive && primitive.isBoolean()
                                ? (entry.getValue().getAsBoolean() ? "1" : "0") : entry.getValue().getAsString();
                            boneVisibilityMap.put(key, value);
                        });
                        geometryBuilder.boneVisibility(boneVisibilityMap);
                    }
                }
                builder.geometry(geometryBuilder.build());
            }
        }

        String displayName = name;
        if (node.has("display_name")) {
            displayName = node.get("display_name").getAsString();
        }
        builder.displayName(displayName);

        if (node.has("friction")) {
            builder.friction(node.get("friction").getAsFloat());
        }

        if (node.has("light_emission")) {
            builder.lightEmission(node.get("light_emission").getAsInt());
        }

        if (node.has("light_dampening")) {
            builder.lightDampening(node.get("light_dampening").getAsInt());
        }

        boolean placeAir = true;
        if (node.has("place_air")) {
            placeAir = node.get("place_air").getAsBoolean();
        }
        builder.placeAir(placeAir);

        if (node.has("transformation")) {
            JsonObject transformation = node.getAsJsonObject("transformation");

            int rotationX = 0;
            int rotationY = 0;
            int rotationZ = 0;
            float scaleX = 1;
            float scaleY = 1;
            float scaleZ = 1;
            float transformX = 0;
            float transformY = 0;
            float transformZ = 0;

            if (transformation.has("rotation")) {
                JsonArray rotation = transformation.getAsJsonArray("rotation");
                rotationX = rotation.get(0).getAsInt();
                rotationY = rotation.get(1).getAsInt();
                rotationZ = rotation.get(2).getAsInt();
            }
            if (transformation.has("scale")) {
                JsonArray scale = transformation.getAsJsonArray("scale");
                scaleX = scale.get(0).getAsFloat();
                scaleY = scale.get(1).getAsFloat();
                scaleZ = scale.get(2).getAsFloat();
            }
            if (transformation.has("translation")) {
                JsonArray translation = transformation.getAsJsonArray("translation");
                transformX = translation.get(0).getAsFloat();
                transformY = translation.get(1).getAsFloat();
                transformZ = translation.get(2).getAsFloat();
            }
            builder.transformation(new TransformationComponent(rotationX, rotationY, rotationZ, scaleX, scaleY, scaleZ, transformX, transformY, transformZ));
        }

        if (node.has("unit_cube")) {
            builder.geometry(GeometryComponent.builder()
                .identifier("minecraft:geometry.full_block")
                .build());
        }

        if (node.has("material_instances")) {
            if (node.get("material_instances") instanceof JsonObject materialInstances) {
                materialInstances.entrySet().forEach(entry -> {
                    String key = entry.getKey();
                    if (entry.getValue() instanceof JsonObject value) {
                        MaterialInstance materialInstance = createMaterialInstanceComponent(value);
                        builder.materialInstance(key, materialInstance);
                    }
                });
            }
        }

        if (node.get("placement_filter") instanceof JsonObject placementFilter) {
            if (placementFilter.get("conditions") instanceof JsonArray conditions) {
                List<PlacementConditions> filter = createPlacementFilterComponent(conditions);
                builder.placementFilter(filter);
            }
        }

        // Tags can be applied so that blocks will match return true when queried for the tag
        // Potentially useful for resource pack creators
        // Ideally we could programmatically extract the tags here https://wiki.bedrock.dev/blocks/block-tags.html
        // This would let us automatically apply the correct vanilla tags to blocks
        // However, its worth noting that vanilla tools do not currently honor these tags anyway
        if (node.get("tags") instanceof JsonArray tags) {
            Set<String> tagsSet = new ObjectOpenHashSet<>();
            tags.forEach(tag -> tagsSet.add(tag.getAsString()));
            builder.tags(tagsSet);
        }

        return new CustomBlockComponentsMapping(builder.build(), extendedBoxComponent);
    }

    /**
     * Creates a {@link BoxComponent} based on a Java block's collision with provided bounds and offsets
     * 
     * @param javaId the block's Java ID
     * @param heightTranslation the height translation of the box
     * @return the {@link BoxComponent}
     */
    private BoxComponent createBoxComponent(int javaId, float heightTranslation) {
        // Some blocks (e.g. plants) have no collision box
        BlockCollision blockCollision = BlockUtils.getCollision(javaId);
        if (blockCollision == null || blockCollision.getBoundingBoxes().length == 0) {
            return BoxComponent.emptyBox();
        }

        float minX = 5;
        float minY = 5;
        float minZ = 5;
        float maxX = -5;
        float maxY = -5;
        float maxZ = -5;
        for (BoundingBox boundingBox : blockCollision.getBoundingBoxes()) {
            double offsetX = boundingBox.getSizeX() * 0.5;
            double offsetY = boundingBox.getSizeY() * 0.5;
            double offsetZ = boundingBox.getSizeZ() * 0.5;

            minX = Math.min(minX, (float) (boundingBox.getMiddleX() - offsetX));
            minY = Math.min(minY, (float) (boundingBox.getMiddleY() - offsetY));
            minZ = Math.min(minZ, (float) (boundingBox.getMiddleZ() - offsetZ));

            maxX = Math.max(maxX, (float) (boundingBox.getMiddleX() + offsetX));
            maxY = Math.max(maxY, (float) (boundingBox.getMiddleY() + offsetY));
            maxZ = Math.max(maxZ, (float) (boundingBox.getMiddleZ() + offsetZ));
        }
        minX = MathUtils.clamp(minX, 0, 1);
        minY = MathUtils.clamp(minY + heightTranslation, 0, 1);
        minZ = MathUtils.clamp(minZ, 0, 1);
        maxX = MathUtils.clamp(maxX, 0, 1);
        maxY = MathUtils.clamp(maxY + heightTranslation, 0, 1);
        maxZ = MathUtils.clamp(maxZ, 0, 1);

        return new BoxComponent(
                16 * (1 - maxX) - 8, // For some odd reason X is mirrored on Bedrock
                16 * minY,
                16 * minZ - 8,
                16 * (maxX - minX),
                16 * (maxY - minY),
                16 * (maxZ - minZ)
        );
    }

    /**
     * Creates a {@link BoxComponent} based on a Java block's collision
     * 
     * @param javaId the block's Java ID
     * @return the {@link BoxComponent}
     */
    private BoxComponent createBoxComponent(int javaId) {
        return createBoxComponent(javaId, 0);
    }

    /**
     * Creates the {@link BoxComponent} for an extended collision box based on a Java block's collision
     * 
     * @param javaId the block's Java ID
     * @return the {@link BoxComponent} or null if the block's collision box would not exceed 16 y units
     */
    private @Nullable BoxComponent createExtendedBoxComponent(int javaId) {
        BlockCollision blockCollision = BlockUtils.getCollision(javaId);
        if (blockCollision == null) {
            return null;
        }
        for (BoundingBox box : blockCollision.getBoundingBoxes()) {
            double maxY = 0.5 * box.getSizeY() + box.getMiddleY();
            if (maxY > 1) {
                return createBoxComponent(javaId, -1);
            }
        }
        return null;
    }

    /**
     * Creates a {@link BoxComponent} from a JSON Node
     * 
     * @param element the JSON node
     * @return the {@link BoxComponent}
     */
    private @Nullable BoxComponent createBoxComponent(JsonElement element) {
        if (element instanceof JsonObject node) {
            if (node.has("origin") && node.has("size")) {
                JsonArray origin = node.getAsJsonArray("origin");
                float originX = origin.get(0).getAsFloat();
                float originY = origin.get(1).getAsFloat();
                float originZ = origin.get(2).getAsFloat();

                JsonArray size = node.getAsJsonArray("size");
                float sizeX = size.get(0).getAsFloat();
                float sizeY = size.get(1).getAsFloat();
                float sizeZ = size.get(2).getAsFloat();

                return new BoxComponent(originX, originY, originZ, sizeX, sizeY, sizeZ);
            }
        }
        return null;
    }

    /**
     * Creates the {@link MaterialInstance} for the passed material instance node and custom block name
     * The name is used as a fallback if no texture is provided by the node
     * 
     * @param node the material instance node
     * @return the {@link MaterialInstance}
     */
    private MaterialInstance createMaterialInstanceComponent(JsonObject node) {
        // Set default values, and use what the user provides if they have provided something
        String texture = null;
        if (node.has("texture")) {
            texture = node.get("texture").getAsString();
        }

        String renderMethod = "opaque";
        if (node.has("render_method")) {
            renderMethod = node.get("render_method").getAsString();
        }

        boolean faceDimming = true;
        if (node.has("face_dimming")) {
            faceDimming = node.get("face_dimming").getAsBoolean();
        }

        boolean ambientOcclusion = true;
        if (node.has("ambient_occlusion")) {
            ambientOcclusion = node.get("ambient_occlusion").getAsBoolean();
        }

        return new GeyserMaterialInstance.Builder()
                .texture(texture)
                .renderMethod(renderMethod)
                .faceDimming(faceDimming)
                .ambientOcclusion(ambientOcclusion)
                .build();
    }

    /**
     * Creates the list of {@link PlacementConditions} for the passed conditions node
     * 
     * @param node the conditions node
     * @return the list of {@link PlacementConditions}
     */
    private List<PlacementConditions> createPlacementFilterComponent(JsonArray node) {
        List<PlacementConditions> conditions = new ArrayList<>();

        // The structure of the placement filter component is the most complex of the current components
        // Each condition effectively separated into two arrays: one of allowed faces, and one of blocks/block Molang queries
        node.forEach(json -> {
            if (!(json instanceof JsonObject condition)) {
                return;
            }
            Set<Face> faces = EnumSet.noneOf(Face.class);
            if (condition.has("allowed_faces")) {
                if (condition.get("allowed_faces") instanceof JsonArray allowedFaces) {
                    allowedFaces.forEach(face -> faces.add(Face.valueOf(face.getAsString().toUpperCase())));
                }
            }

            LinkedHashMap<String, BlockFilterType> blockFilters = new LinkedHashMap<>();
            if (condition.has("block_filter")) {
                if (condition.get("block_filter") instanceof JsonArray blockFilter) {
                    blockFilter.forEach(filter -> {
                        if (filter instanceof JsonObject jsonObject) {
                            if (jsonObject.has("tags")) {
                                JsonElement tags = jsonObject.get("tags");
                                blockFilters.put(tags.getAsString(), BlockFilterType.TAG);
                            }
                        } else if (filter instanceof JsonPrimitive primitive && primitive.isString()) {
                            blockFilters.put(filter.getAsString(), BlockFilterType.BLOCK);
                        }
                    });
                }
            }

            conditions.add(new PlacementConditions(faces, blockFilters));
        });

        return conditions;
    }

    /**
     * Splits the given java state identifier into an array of property=value pairs
     * 
     * @param state the java state identifier
     * @return the array of property=value pairs
     */
    private String[] splitStateString(String state) {
        int openBracketIndex = state.indexOf("[");

        String states = state.substring(openBracketIndex + 1, state.length() - 1);
        return states.split(",");
    }

}
