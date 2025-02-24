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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.predicate.CustomItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.RangeDispatchPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.condition.ConditionPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.CustomModelDataString;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.MatchPredicateProperty;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.components.DataComponentReaders;
import org.geysermc.geyser.registry.mappings.util.CustomBlockMapping;
import org.geysermc.geyser.registry.mappings.util.MappingsUtil;
import org.geysermc.geyser.registry.mappings.util.NodeReader;
import org.geysermc.geyser.util.MinecraftKey;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;

public class MappingsReader_v2 extends MappingsReader {

    @Override
    public void readItemMappings(Path file, JsonObject mappingsRoot, BiConsumer<Identifier, CustomItemDefinition> consumer) {
        readItemMappingsV2(file, mappingsRoot, consumer);
    }

    public void readItemMappingsV2(Path file, JsonObject mappingsRoot, BiConsumer<Identifier, CustomItemDefinition> consumer) {
        JsonObject items = mappingsRoot.getAsJsonObject("items");

        if (items != null) {
            items.entrySet().forEach(entry -> {
                if (entry.getValue() instanceof JsonArray array) {
                    array.forEach(definition -> {
                        try {
                            readItemDefinitionEntry(definition, Identifier.of(entry.getKey()), null, consumer);
                        } catch (InvalidCustomMappingsFileException exception) {
                            GeyserImpl.getInstance().getLogger().error(
                                "Error reading definition for item " + entry.getKey() + " in custom mappings file: " + file.toString(), exception);
                        }
                    });
                } else {
                    GeyserImpl.getInstance().getLogger().error("Item definitions key " + entry.getKey() + " was not an array!");
                }
            });
        }
    }

    @Override
    public void readBlockMappings(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomBlockMapping> consumer) {
        JsonElement blocks = mappingsRoot.get("blocks");
        if (blocks != null) {
            throw new UnsupportedOperationException("Unimplemented; use the v1 format of block mappings");
        }
    }

    private void readItemDefinitionEntry(JsonElement data, Identifier itemIdentifier, Identifier model,
                                         BiConsumer<Identifier, CustomItemDefinition> definitionConsumer) throws InvalidCustomMappingsFileException {
        String context = "item definition(s) for Java item " + itemIdentifier;

        String type = MappingsUtil.readOrDefault(data, "type", NodeReader.NON_EMPTY_STRING, "definition", context);
        if (type.equals("group")) {
            // Read model of group if it's present, or default to the model of the parent group, if that's present
            // If the parent group model is not present (or there is no parent group), and this group also doesn't have a model, then it is expected the definitions supply their model themselves
            Identifier groupModel = MappingsUtil.readOrDefault(data, "model", NodeReader.IDENTIFIER, model, context);

            // The method above should have already thrown a properly formatted error if data is not a JSON object
            JsonElement definitions = data.getAsJsonObject().get("definitions");

            if (definitions == null || !definitions.isJsonArray()) {
                throw new InvalidCustomMappingsFileException("reading item definitions in group", "group has no definitions key, or it wasn't an array", context);
            } else {
                for (JsonElement definition : definitions.getAsJsonArray()) {
                    // Recursively read all the entries in the group - they can be more groups or definitions
                    readItemDefinitionEntry(definition, itemIdentifier, groupModel, definitionConsumer);
                }
            }
        } else if (type.equals("definition")) {
            CustomItemDefinition customItemDefinition = readItemMappingEntry(model, data);
            definitionConsumer.accept(itemIdentifier, customItemDefinition);
        } else {
            throw new InvalidCustomMappingsFileException("reading item definition", "unknown definition type " + type, context);
        }
    }

    @Override
    public CustomItemDefinition readItemMappingEntry(Identifier parentModel, JsonElement element) throws InvalidCustomMappingsFileException {
        Identifier bedrockIdentifier = MappingsUtil.readOrThrow(element, "bedrock_identifier", NodeReader.IDENTIFIER, "item definition");
        // We now know the Bedrock identifier, make a base context so that the error can be easily located in the JSON file
        String context = "item definition (bedrock identifier=" + bedrockIdentifier + ")";

        Identifier model = MappingsUtil.readOrDefault(element, "model", NodeReader.IDENTIFIER, parentModel, context);

        if (model == null) {
            throw new InvalidCustomMappingsFileException("reading item model", "no model present", context);
        }

        if (bedrockIdentifier.namespace().equals(Key.MINECRAFT_NAMESPACE)) {
            bedrockIdentifier = Identifier.of(Constants.GEYSER_CUSTOM_NAMESPACE, bedrockIdentifier.path()); // Use geyser_custom namespace when no namespace or the minecraft namespace was given
        }
        CustomItemDefinition.Builder builder = CustomItemDefinition.builder(bedrockIdentifier, model);

        MappingsUtil.readIfPresent(element, "display_name", builder::displayName, NodeReader.NON_EMPTY_STRING, context);
        MappingsUtil.readIfPresent(element, "priority", builder::priority, NodeReader.INT, context);

        // Mappings util methods used above already threw a properly formatted error if the element is not a JSON object
        readPredicates(builder, element.getAsJsonObject().get("predicate"), context);
        MappingsUtil.readIfPresent(element, "predicate_strategy", builder::predicateStrategy, NodeReader.PREDICATE_STRATEGY, context);

        builder.bedrockOptions(readBedrockOptions(element.getAsJsonObject().get("bedrock_options"), context));

        JsonElement componentsElement = element.getAsJsonObject().get("components");
        if (componentsElement != null) {
            if (componentsElement instanceof JsonObject components) {
                for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
                    DataComponentReaders.readDataComponent(builder, MinecraftKey.key(entry.getKey()), entry.getValue(), context);
                }
            } else {
                throw new InvalidCustomMappingsFileException("reading components", "components key must be an object", context);
            }
        }

        return builder.build();
    }

    private CustomItemBedrockOptions.Builder readBedrockOptions(JsonElement element, String baseContext) throws InvalidCustomMappingsFileException {
        CustomItemBedrockOptions.Builder builder = CustomItemBedrockOptions.builder();
        if (element == null) {
            return builder;
        }

        String[] context = {"bedrock options", baseContext};
        MappingsUtil.readIfPresent(element, "icon", builder::icon, NodeReader.NON_EMPTY_STRING, context);
        MappingsUtil.readIfPresent(element, "allow_offhand", builder::allowOffhand, NodeReader.BOOLEAN, context);
        MappingsUtil.readIfPresent(element, "display_handheld", builder::displayHandheld, NodeReader.BOOLEAN, context);
        MappingsUtil.readIfPresent(element, "protection_value", builder::protectionValue, NodeReader.NON_NEGATIVE_INT, context);
        MappingsUtil.readIfPresent(element, "creative_category", builder::creativeCategory, NodeReader.CREATIVE_CATEGORY, context);
        MappingsUtil.readIfPresent(element, "creative_group", builder::creativeGroup, NodeReader.NON_EMPTY_STRING, context);
        MappingsUtil.readArrayIfPresent(element, "tags", tags -> builder.tags(new HashSet<>(tags)), NodeReader.IDENTIFIER, context);

        return builder;
    }

    private void readPredicates(CustomItemDefinition.Builder builder, JsonElement element, String context) throws InvalidCustomMappingsFileException {
        if (element == null) {
            return;
        }

        if (element.isJsonObject()) {
            readPredicate(builder, element, context);
        } else if (element.isJsonArray()) {
            for (JsonElement predicate : element.getAsJsonArray()) {
                readPredicate(builder, predicate, context);
            }
        } else {
            throw new InvalidCustomMappingsFileException("reading predicates", "expected predicate key to be a list of predicates or a predicate", context);
        }
    }

    private void readPredicate(CustomItemDefinition.Builder builder, @NonNull JsonElement element, String baseContext) throws InvalidCustomMappingsFileException {
        String type = MappingsUtil.readOrThrow(element, "type", NodeReader.NON_EMPTY_STRING, "predicate", baseContext);
        String[] context = {type + " predicate", baseContext};

        switch (type) {
            case "condition" -> {
                ConditionPredicateProperty<?> conditionProperty = MappingsUtil.readOrThrow(element, "property", NodeReader.CONDITION_PREDICATE_PROPERTY, context);
                boolean expected = MappingsUtil.readOrDefault(element, "expected", NodeReader.BOOLEAN, true, context);

                if (!conditionProperty.requiresData) {
                    builder.predicate(CustomItemPredicate.condition((ConditionPredicateProperty<Void>) conditionProperty, expected));
                } else if (conditionProperty == ConditionPredicateProperty.CUSTOM_MODEL_DATA) {
                    int index = MappingsUtil.readOrDefault(element, "index", NodeReader.NON_NEGATIVE_INT, 0, context);
                    builder.predicate(CustomItemPredicate.condition(ConditionPredicateProperty.CUSTOM_MODEL_DATA, expected, index));
                } else if (conditionProperty == ConditionPredicateProperty.HAS_COMPONENT) {
                    Identifier component = MappingsUtil.readOrThrow(element, "component", NodeReader.IDENTIFIER, context);
                    builder.predicate(CustomItemPredicate.condition(ConditionPredicateProperty.HAS_COMPONENT, expected, component));
                } else {
                    throw new InvalidCustomMappingsFileException("reading condition predicate", "unimplemented reading of condition predicate property!", context);
                }
            }
            case "match" -> {
                MatchPredicateProperty<?> property = MappingsUtil.readOrThrow(element, "property", NodeReader.MATCH_PREDICATE_PROPERTY, context);

                if (property == MatchPredicateProperty.CHARGE_TYPE) {
                    builder.predicate(CustomItemPredicate.match(MatchPredicateProperty.CHARGE_TYPE,
                        MappingsUtil.readOrThrow(element, "value", NodeReader.CHARGE_TYPE, context)));
                } else if (property == MatchPredicateProperty.TRIM_MATERIAL || property == MatchPredicateProperty.CONTEXT_DIMENSION) {
                    builder.predicate(CustomItemPredicate.match((MatchPredicateProperty<Identifier>) property,
                        MappingsUtil.readOrThrow(element, "value", NodeReader.IDENTIFIER, context)));
                } else if (property == MatchPredicateProperty.CUSTOM_MODEL_DATA) {
                    builder.predicate(CustomItemPredicate.match(MatchPredicateProperty.CUSTOM_MODEL_DATA,
                        new CustomModelDataString(MappingsUtil.readOrThrow(element, "value", NodeReader.STRING, context),
                            MappingsUtil.readOrDefault(element, "index", NodeReader.NON_NEGATIVE_INT, 0, context))));
                } else {
                    throw new InvalidCustomMappingsFileException("reading match predicate", "unimplemented reading of match predicate property!", context);
                }
            }
            case "range_dispatch" -> {
                RangeDispatchPredicateProperty property = MappingsUtil.readOrThrow(element, "property", NodeReader.RANGE_DISPATCH_PREDICATE_PROPERTY, context);

                double threshold = MappingsUtil.readOrThrow(element, "threshold", NodeReader.DOUBLE, context);
                double scale = MappingsUtil.readOrDefault(element, "scale", NodeReader.DOUBLE, 1.0, context);
                boolean normalizeIfPossible = MappingsUtil.readOrDefault(element, "normalize", NodeReader.BOOLEAN, false, context);
                int index = MappingsUtil.readOrDefault(element, "index", NodeReader.NON_NEGATIVE_INT, 0, context);

                builder.predicate(CustomItemPredicate.rangeDispatch(property, threshold, scale, normalizeIfPossible, index));
            }
            default -> throw new InvalidCustomMappingsFileException("reading predicate", "unknown predicate type " + type, context);
        }
    }

    @Override
    public CustomBlockMapping readBlockMappingEntry(String identifier, JsonElement node) throws InvalidCustomMappingsFileException {
        throw new InvalidCustomMappingsFileException("Unimplemented; use the v1 format of block mappings");
    }
}
