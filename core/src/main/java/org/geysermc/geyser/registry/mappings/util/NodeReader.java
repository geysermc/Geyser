/*
 * Copyright (c) 2025 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.registry.mappings.util;

import com.google.gson.JsonPrimitive;
import org.geysermc.geyser.api.item.custom.v2.component.Consumable;
import org.geysermc.geyser.api.item.custom.v2.component.Equippable;
import org.geysermc.geyser.api.item.custom.v2.predicate.PredicateStrategy;
import org.geysermc.geyser.api.item.custom.v2.predicate.RangeDispatchPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.condition.ConditionPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.ChargeType;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.MatchPredicateProperty;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@FunctionalInterface
public interface NodeReader<T> {

    NodeReader<Integer> INT = node -> {
        double i = node.getAsDouble();
        if (i == (int) i) { // Make sure the number is round
            return (int) i;
        }
        throw new InvalidCustomMappingsFileException("expected node to be an integer");
    };

    NodeReader<Integer> NON_NEGATIVE_INT = INT.validate(i -> i >= 0, "integer must be non-negative");

    NodeReader<Integer> POSITIVE_INT = INT.validate(i -> i > 0, "integer must be positive");

    NodeReader<Double> DOUBLE = JsonPrimitive::getAsDouble;

    NodeReader<Double> NON_NEGATIVE_DOUBLE = DOUBLE.validate(d -> d >= 0, "number must be non-negative");

    NodeReader<Double> POSITIVE_DOUBLE = DOUBLE.validate(d -> d > 0, "number must be positive");

    NodeReader<Boolean> BOOLEAN = node -> {
        // Not directly using getAsBoolean here since that doesn't convert integers and doesn't throw an error when the string is not "true" or "false"
        if (node.isString()) {
            String s = node.getAsString();
            if (s.equals("true")) {
                return true;
            } else if (s.equals("false")) {
                return false;
            }
        } else if (node.isNumber() && node.getAsNumber() instanceof Integer i) {
            if (i == 1) {
                return true;
            } else if (i == 0) {
                return false;
            }
        } else if (node.isBoolean()) {
            return node.getAsBoolean();
        }
        throw new InvalidCustomMappingsFileException("expected node to be a boolean");
    };

    NodeReader<String> STRING = JsonPrimitive::getAsString;

    NodeReader<String> NON_EMPTY_STRING = STRING.validate(s -> !s.isEmpty(), "string must not be empty");

    NodeReader<Identifier> IDENTIFIER = NON_EMPTY_STRING.andThen(Identifier::of);

    NodeReader<CreativeCategory> CREATIVE_CATEGORY = NON_EMPTY_STRING.andThen(CreativeCategory::fromName).validate(Objects::nonNull, "unknown creative category");

    NodeReader<PredicateStrategy> PREDICATE_STRATEGY = ofEnum(PredicateStrategy.class);

    NodeReader<ConditionPredicateProperty<?>> CONDITION_PREDICATE_PROPERTY = ofMap(
        Map.of(
            "broken", ConditionPredicateProperty.BROKEN,
            "damaged", ConditionPredicateProperty.DAMAGED,
            "custom_model_data", ConditionPredicateProperty.CUSTOM_MODEL_DATA,
            "has_component", ConditionPredicateProperty.HAS_COMPONENT
        )
    );

    NodeReader<MatchPredicateProperty<?>> MATCH_PREDICATE_PROPERTY = ofMap(
        Map.of(
            "charge_type", MatchPredicateProperty.CHARGE_TYPE,
            "trim_material", MatchPredicateProperty.TRIM_MATERIAL,
            "context_dimension", MatchPredicateProperty.CONTEXT_DIMENSION,
            "custom_model_data", MatchPredicateProperty.CUSTOM_MODEL_DATA
        )
    );

    NodeReader<ChargeType> CHARGE_TYPE = ofEnum(ChargeType.class);

    NodeReader<RangeDispatchPredicateProperty> RANGE_DISPATCH_PREDICATE_PROPERTY = ofEnum(RangeDispatchPredicateProperty.class);

    NodeReader<Consumable.Animation> CONSUMABLE_ANIMATION = ofEnum(Consumable.Animation.class);

    NodeReader<Equippable.EquipmentSlot> EQUIPMENT_SLOT = ofEnum(Equippable.EquipmentSlot.class);

    static <E extends Enum<E>> NodeReader<E> ofEnum(Class<E> clazz) {
        return NON_EMPTY_STRING.andThen(String::toUpperCase).andThen(s -> {
            try {
                return Enum.valueOf(clazz, s);
            } catch (IllegalArgumentException exception) {
                throw new InvalidCustomMappingsFileException("unknown element, must be one of ["
                    + String.join(", ", Arrays.stream(clazz.getEnumConstants()).map(E::toString).toArray(String[]::new)).toLowerCase() + "]");
            }
        });
    }

    static <T> NodeReader<T> ofMap(Map<String, T> map) {
        return NON_EMPTY_STRING.andThen(String::toLowerCase).andThen(s -> {
            T value = map.get(s);
            if (value == null) {
                throw new InvalidCustomMappingsFileException("unknown element, must be one of ["
                    + String.join(", ", map.keySet()).toLowerCase() + "]");
            }
            return value;
        });
    }

    static NodeReader<Integer> boundedInt(int min, int max) {
        return INT.validate(i -> i >= min && i <= max, "integer must be in range [" + min + ", " + max + "]");
    }

    /**
     * {@link NodeReader#read(JsonPrimitive, String, String...)} is preferably used as that properly formats the error when one is thrown.
     */
    T read(JsonPrimitive node) throws InvalidCustomMappingsFileException;

    default T read(JsonPrimitive node, String task, String... context) throws InvalidCustomMappingsFileException {
        try {
            return read(node);
        } catch (Exception exception) {
            throw new InvalidCustomMappingsFileException(task, exception.getMessage() + " (node was " + node.toString() + ")", context);
        }
    }

    default <V> NodeReader<V> andThen(After<T, V> after) {
        return node -> after.apply(read(node));
    }

    default NodeReader<T> validate(Predicate<T> validator, String error) {
        return andThen(v -> {
            if (!validator.test(v)) {
                throw new InvalidCustomMappingsFileException(error);
            }
            return v;
        });
    }

    @FunctionalInterface
    interface After<V, T> {

        T apply(V value) throws InvalidCustomMappingsFileException;
    }
}
