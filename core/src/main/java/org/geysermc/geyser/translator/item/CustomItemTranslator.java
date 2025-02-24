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

package org.geysermc.geyser.translator.item;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.protocol.bedrock.data.TrimMaterial;
import org.geysermc.geyser.api.item.custom.v2.predicate.CustomItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.PredicateStrategy;
import org.geysermc.geyser.api.item.custom.v2.predicate.condition.ConditionPredicateProperty;
import org.geysermc.geyser.item.custom.predicate.ConditionPredicate;
import org.geysermc.geyser.item.custom.predicate.RangeDispatchPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.ChargeType;
import org.geysermc.geyser.item.custom.predicate.MatchPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.CustomModelDataString;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.MatchPredicateProperty;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.item.GeyserCustomMappingData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.RegistryEntryData;
import org.geysermc.geyser.util.MinecraftKey;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ArmorTrim;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.CustomModelData;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.geysermc.geyser.registry.type.ItemMapping;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * This is only a separate class for testing purposes so we don't have to load in GeyserImpl in ItemTranslator.
 */
public final class CustomItemTranslator {
    private static final Key FALLBACK_MODEL = MinecraftKey.key("air");

    @Nullable
    public static ItemDefinition getCustomItem(GeyserSession session, int stackSize, DataComponents components, ItemMapping mapping) {
        if (components == null) {
            return null;
        }

        Multimap<Key, GeyserCustomMappingData> allCustomItems = mapping.getCustomItemDefinitions();
        if (allCustomItems == null) {
            return null;
        }

        Key itemModel = components.getOrDefault(DataComponentTypes.ITEM_MODEL, FALLBACK_MODEL);
        Collection<GeyserCustomMappingData> customItems = allCustomItems.get(itemModel);
        if (customItems.isEmpty()) {
            return null;
        }

        // Cache predicate values so they're not recalculated every time when there are multiple item definitions using the same predicates
        Object2BooleanMap<CustomItemPredicate> calculatedPredicates = new Object2BooleanOpenHashMap<>();
        for (GeyserCustomMappingData customMapping : customItems) {
            boolean needsOnlyOneMatch = customMapping.definition().predicateStrategy() == PredicateStrategy.OR;
            boolean allMatch = true;

            for (CustomItemPredicate predicate : customMapping.definition().predicates()) {
                boolean value = calculatedPredicates.computeIfAbsent(predicate, x -> predicateMatches(session, predicate, stackSize, components));
                if (!value) {
                    allMatch = false;
                    break;
                } else if (needsOnlyOneMatch) {
                    break;
                }
            }
            if (allMatch) {
                return customMapping.itemDefinition();
            }
        }
        return null;
    }

    private static boolean predicateMatches(GeyserSession session, CustomItemPredicate predicate, int stackSize, DataComponents components) {
        if (predicate instanceof ConditionPredicate<?> condition) {
            ConditionPredicateProperty<?> property = condition.property();
            boolean expected = condition.expected();
            if (property == ConditionPredicateProperty.BROKEN) {
                return nextDamageWillBreak(components) == expected;
            } else if (property == ConditionPredicateProperty.DAMAGED) {
                return isDamaged(components) == expected;
            } else if (property == ConditionPredicateProperty.CUSTOM_MODEL_DATA) {
                Integer index = (Integer) condition.data();
                return getCustomBoolean(components, index) == expected;
            } else if (property == ConditionPredicateProperty.HAS_COMPONENT) {
                Identifier identifier = (Identifier) condition.data();
                if (identifier == null) {
                    return false;
                }
                Key component = MinecraftKey.identifierToKey(identifier);
                for (DataComponentType<?> componentType : components.getDataComponents().keySet()) {
                    if (componentType.getKey().equals(component)) {
                        return expected;
                    }
                }
                return !expected;
            }
        } else if (predicate instanceof MatchPredicate<?> match) { // TODO not much of a fan of the casts here, find a solution for the types?
            if (match.property() == MatchPredicateProperty.CHARGE_TYPE) {
                ChargeType expected = (ChargeType) match.data();
                List<ItemStack> charged = components.get(DataComponentTypes.CHARGED_PROJECTILES);
                if (charged == null || charged.isEmpty()) {
                    return expected == ChargeType.NONE;
                } else if (expected == ChargeType.ROCKET) {
                    for (ItemStack projectile : charged) {
                        if (projectile.getId() == Items.FIREWORK_ROCKET.javaId()) {
                            return true;
                        }
                    }
                    return false;
                }
                return true;
            } else if (match.property() == MatchPredicateProperty.TRIM_MATERIAL) {
                Identifier material = (Identifier) match.data();
                ArmorTrim trim = components.get(DataComponentTypes.TRIM);
                if (trim == null || trim.material().isCustom()) {
                    return false;
                }
                RegistryEntryData<TrimMaterial> registered = session.getRegistryCache().trimMaterials().entryById(trim.material().id());
                return MinecraftKey.identifierToKey(material).equals(registered.key());
            } else if (match.property() == MatchPredicateProperty.CONTEXT_DIMENSION) {
                Identifier dimension = (Identifier) match.data();
                RegistryEntryData<JavaDimension> registered = session.getRegistryCache().dimensions().entryByValue(session.getDimensionType());
                return MinecraftKey.identifierToKey(dimension).equals(registered.key());
            } else if (match.property() == MatchPredicateProperty.CUSTOM_MODEL_DATA) {
                CustomModelDataString expected = (CustomModelDataString) match.data();
                return expected.value().equals(getSafeCustomModelData(components, CustomModelData::strings, expected.index()));
            }
        } else if (predicate instanceof RangeDispatchPredicate rangeDispatch) {
            double propertyValue = switch (rangeDispatch.property()) {
                case BUNDLE_FULLNESS -> {
                    List<ItemStack> stacks = components.get(DataComponentTypes.BUNDLE_CONTENTS);
                    if (stacks == null) {
                        yield 0;
                    }
                    int bundleWeight = 0;
                    for (ItemStack stack : stacks) {
                        bundleWeight += stack.getAmount();
                    }
                    yield bundleWeight;
                }
                case DAMAGE -> tryNormalize(rangeDispatch, components.get(DataComponentTypes.DAMAGE), components.get(DataComponentTypes.MAX_DAMAGE));
                case COUNT -> tryNormalize(rangeDispatch, stackSize, components.get(DataComponentTypes.MAX_STACK_SIZE));
                case CUSTOM_MODEL_DATA -> getCustomFloat(components, rangeDispatch.index());
            } * rangeDispatch.scale();
            return propertyValue >= rangeDispatch.threshold();
        }

        throw new IllegalStateException("Unimplemented predicate type: " + predicate);
    }

    private static boolean getCustomBoolean(DataComponents components, Integer index) {
        if (index == null) {
            return false;
        }
        Boolean b = getSafeCustomModelData(components, CustomModelData::flags, index);
        return b != null && b;
    }

    private static float getCustomFloat(DataComponents components, int index) {
        Float f = getSafeCustomModelData(components, CustomModelData::floats, index);
        return f == null ? 0.0F : f;
    }

    private static <T> T getSafeCustomModelData(DataComponents components, Function<CustomModelData, List<T>> listGetter, int index) {
        CustomModelData modelData = components.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (modelData == null || index < 0) {
            return null;
        }
        List<T> list = listGetter.apply(modelData);
        if (index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    private static double tryNormalize(RangeDispatchPredicate predicate, @Nullable Integer value, @Nullable Integer max) {
        if (value == null) {
            return 0.0;
        } else if (max == null) {
            return value;
        } else if (!predicate.normalizeIfPossible()) {
            return Math.min(value, max);
        }
        return Math.max(0.0, Math.min(1.0, (double) value / max));
    }

    /* These three functions are based off their Mojmap equivalents from 1.21.3 */
    private static boolean nextDamageWillBreak(DataComponents components) {
        return isDamageableItem(components) && components.getOrDefault(DataComponentTypes.DAMAGE, 0) >= components.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0) - 1;
    }

    private static boolean isDamaged(DataComponents components) {
        return isDamageableItem(components) && components.getOrDefault(DataComponentTypes.DAMAGE, 0) > 0;
    }

    private static boolean isDamageableItem(DataComponents components) {
        return components.get(DataComponentTypes.UNBREAKABLE) == null && components.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0) > 0;
    }

    private CustomItemTranslator() {
    }
}
