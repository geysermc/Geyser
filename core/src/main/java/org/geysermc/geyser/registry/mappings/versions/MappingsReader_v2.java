/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.item.custom.v2.BedrockCreativeTab;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.predicate.CustomItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.ItemPredicateType;
import org.geysermc.geyser.api.item.custom.v2.predicate.data.ConditionPredicateData;
import org.geysermc.geyser.api.item.custom.v2.predicate.data.CustomModelDataPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.data.match.ChargeType;
import org.geysermc.geyser.api.item.custom.v2.predicate.data.match.MatchPredicateData;
import org.geysermc.geyser.api.item.custom.v2.predicate.data.match.MatchPredicateProperty;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.components.DataComponentReaders;
import org.geysermc.geyser.registry.mappings.util.CustomBlockMapping;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;
import java.util.function.BiConsumer;

public class MappingsReader_v2 extends MappingsReader {

    @Override
    public void readItemMappings(Path file, JsonNode mappingsRoot, BiConsumer<String, CustomItemDefinition> consumer) {
        readItemMappingsV2(file, mappingsRoot, consumer);
    }

    public void readItemMappingsV2(Path file, JsonNode mappingsRoot, BiConsumer<String, CustomItemDefinition> consumer) {
        JsonNode itemModels = mappingsRoot.get("items");

        if (itemModels != null && itemModels.isObject()) {
            itemModels.fields().forEachRemaining(entry -> {
                if (entry.getValue().isArray()) {
                    entry.getValue().forEach(data -> {
                        try {
                            CustomItemDefinition customItemDefinition = readItemMappingEntry(entry.getKey(), data);
                            consumer.accept(entry.getKey(), customItemDefinition);
                        } catch (InvalidCustomMappingsFileException e) {
                            GeyserImpl.getInstance().getLogger().error("Error in registering items for custom mapping file: " + file.toString(), e);
                        }
                    });
                }
            });
        }
    }

    @Override
    public void readBlockMappings(Path file, JsonNode mappingsRoot, BiConsumer<String, CustomBlockMapping> consumer) {
        // TODO
    }

    @Override
    public CustomItemDefinition readItemMappingEntry(String identifier, JsonNode node) throws InvalidCustomMappingsFileException {
        if (node == null || !node.isObject()) {
            throw new InvalidCustomMappingsFileException("Invalid item mappings entry");
        }

        JsonNode bedrockIdentifierNode = node.get("bedrock_identifier");
        JsonNode model = node.get("model");

        if (bedrockIdentifierNode == null || !bedrockIdentifierNode.isTextual() || bedrockIdentifierNode.asText().isEmpty()) {
            throw new InvalidCustomMappingsFileException("An item entry has no bedrock identifier");
        }
        if (model == null || !model.isTextual() || model.asText().isEmpty()) {
            throw new InvalidCustomMappingsFileException("An item entry has no model");
        }

        Key bedrockIdentifier = Key.key(bedrockIdentifierNode.asText());
        if (bedrockIdentifier.namespace().equals(Key.MINECRAFT_NAMESPACE)) {
            bedrockIdentifier = Key.key(Constants.GEYSER_CUSTOM_NAMESPACE, bedrockIdentifier.value());
        }
        CustomItemDefinition.Builder builder = CustomItemDefinition.builder(bedrockIdentifier, Key.key(model.asText()));

        if (node.has("display_name")) {
            builder.displayName(node.get("display_name").asText());
        }

        readPredicates(builder, node.get("predicate"));

        builder.bedrockOptions(readBedrockOptions(node.get("bedrock_options")));

        DataComponents components = new DataComponents(new HashMap<>()); // TODO faster map ?
        JsonNode componentsNode = node.get("components");
        if (componentsNode != null && componentsNode.isObject()) {
            componentsNode.fields().forEachRemaining(entry -> {
                try {
                    DataComponentReaders.readDataComponent(components, Key.key(entry.getKey()), entry.getValue());
                } catch (InvalidCustomMappingsFileException e) {
                    GeyserImpl.getInstance().getLogger().error("Error reading component " + entry.getKey() + " for item model " + model.textValue(), e);
                }
            });
        }
        builder.components(components);

        return builder.build();
    }

    private CustomItemBedrockOptions.Builder readBedrockOptions(JsonNode node) {
        CustomItemBedrockOptions.Builder builder = CustomItemBedrockOptions.builder();
        if (node == null || !node.isObject()) {
            return builder;
        }

        if (node.has("icon")) {
            builder.icon(node.get("icon").asText());
        }

        if (node.has("creative_category")) {
            builder.creativeCategory(BedrockCreativeTab.valueOf(node.get("creative_category").asText().toUpperCase()));
        }

        if (node.has("creative_group")) {
            builder.creativeGroup(node.get("creative_group").asText());
        }

        if (node.has("allow_offhand")) {
            builder.allowOffhand(node.get("allow_offhand").asBoolean());
        }

        if (node.has("display_handheld")) {
            builder.displayHandheld(node.get("display_handheld").asBoolean());
        }

        if (node.has("texture_size")) {
            builder.textureSize(node.get("texture_size").asInt());
        }

        if (node.has("render_offsets")) {
            JsonNode tmpNode = node.get("render_offsets");

            builder.renderOffsets(fromJsonNode(tmpNode));
        }

        if (node.get("tags") instanceof ArrayNode tags) {
            Set<String> tagsSet = new ObjectOpenHashSet<>();
            tags.forEach(tag -> tagsSet.add(tag.asText()));
            builder.tags(tagsSet);
        }

        return builder;
    }

    private void readPredicates(CustomItemDefinition.Builder builder, JsonNode node) throws InvalidCustomMappingsFileException {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            readPredicate(builder, node);
        } else if (node.isArray()) {
            node.forEach(predicate -> {
                try {
                    readPredicate(builder, predicate);
                } catch (InvalidCustomMappingsFileException e) {
                    GeyserImpl.getInstance().getLogger().error("Error in reading predicate", e); // TODO log this better
                }
            });
        } else {
            throw new InvalidCustomMappingsFileException("Expected predicate key to be a list of predicates or a predicate");
        }
    }

    private void readPredicate(CustomItemDefinition.Builder builder, @NonNull JsonNode node) throws InvalidCustomMappingsFileException {
        if (!node.isObject()) {
            throw new InvalidCustomMappingsFileException("Expected predicate to be an object");
        }

        JsonNode typeNode = node.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new InvalidCustomMappingsFileException("Predicate missing type key");
        }

        ItemPredicateType<?> type = ItemPredicateType.getType(typeNode.asText());
        JsonNode propertyNode = node.get("property");
        if (propertyNode == null || !propertyNode.isTextual()) {
            throw new InvalidCustomMappingsFileException("Predicate missing property key");
        }

        if (type == ItemPredicateType.CONDITION) {
            try {
                ConditionPredicateData.ConditionProperty property = ConditionPredicateData.ConditionProperty.valueOf(propertyNode.asText().toUpperCase());
                JsonNode expected = node.get("expected");
                JsonNode index = node.get("index");

                builder.predicate(new CustomItemPredicate<>(ItemPredicateType.CONDITION, new ConditionPredicateData(property,
                    expected == null || expected.asBoolean(), index == null || !index.isIntegralNumber() ? 0 : index.asInt())));
            } catch (IllegalArgumentException exception) {
                throw new InvalidCustomMappingsFileException("Unknown property " + propertyNode.asText());
            }
        } else if (type == ItemPredicateType.MATCH) {
            MatchPredicateProperty<?> property = MatchPredicateProperty.getProperty(propertyNode.asText());
            if (property == null) {
                throw new InvalidCustomMappingsFileException("Unknown property " + propertyNode.asText());
            }

            JsonNode value = node.get("value");
            if (value == null || !value.isTextual()) {
                throw new InvalidCustomMappingsFileException("Predicate missing value key");
            }

            if (property == MatchPredicateProperty.CHARGE_TYPE) {
                try {
                    ChargeType chargeType = ChargeType.valueOf(value.asText().toUpperCase());
                    builder.predicate(new CustomItemPredicate<>(ItemPredicateType.MATCH,
                        new MatchPredicateData<>(MatchPredicateProperty.CHARGE_TYPE, chargeType)));
                } catch (IllegalArgumentException exception) {
                    throw new InvalidCustomMappingsFileException("Unknown charge type " + value.asText());
                }
            } else if (property == MatchPredicateProperty.TRIM_MATERIAL || property == MatchPredicateProperty.CONTEXT_DIMENSION) {
                builder.predicate(new CustomItemPredicate<>(ItemPredicateType.MATCH,
                    new MatchPredicateData<>((MatchPredicateProperty<Key>) property, Key.key(value.asText())))); // TODO
            } else if (property == MatchPredicateProperty.CUSTOM_MODEL_DATA) {
                JsonNode index = node.get("index");
                if (index == null || !index.isIntegralNumber()) {
                    throw new InvalidCustomMappingsFileException("Predicate missing index key");
                }
                builder.predicate(new CustomItemPredicate<>(ItemPredicateType.MATCH,
                    new MatchPredicateData<>(MatchPredicateProperty.CUSTOM_MODEL_DATA, new CustomModelDataPredicate<>(value.asText(), index.asInt()))));
            }
        }
    }

    @Override
    public CustomBlockMapping readBlockMappingEntry(String identifier, JsonNode node) throws InvalidCustomMappingsFileException {
        return null; // TODO
    }
}
