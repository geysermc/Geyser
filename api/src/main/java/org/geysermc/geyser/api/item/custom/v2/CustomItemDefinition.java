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

package org.geysermc.geyser.api.item.custom.v2;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.item.custom.v2.component.DataComponentMap;
import org.geysermc.geyser.api.item.custom.v2.component.DataComponent;
import org.geysermc.geyser.api.item.custom.v2.predicate.CustomItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.PredicateStrategy;
import org.geysermc.geyser.api.util.Identifier;

import java.util.List;

/**
 * This is used to define a custom item and its properties for a specific Java item and item model definition combination.
 *
 * <p>A custom item definition will be used for all item stacks that match the Java item and item model this item is for.
 * Additionally, predicates can be added that allow fine-grained control as to when to use this custom item. These predicates are similar
 * to the predicates available in Java item model definitions.</p>
 *
 * <p>In Geyser, all registered custom item definitions for a Java item model will be checked in a specific order:</p>
 *
 * <ol>
 *     <li>First by checking their priority values, higher priority values going first.</li>
 *     <li>Then by checking if they both have a similar range dispatch predicate, the one with the highest threshold going first.</li>
 *     <li>Lastly by the amount of predicates, from most to least.</li>
 * </ol>
 *
 * <p>This ensures predicates will be checked in the correct order. In most cases, specifying a priority value isn't necessary, but it can be added to ensure the intended order. </p>
 */
public interface CustomItemDefinition {

    /**
     * The Bedrock identifier for this custom item. It cannot be in the {@code minecraft} namespace.
     */
    @NonNull Identifier bedrockIdentifier();

    /**
     * The display name of the item. If none is set, the display name is taken from the item's Bedrock identifier.
     */
    @NonNull String displayName();

    /**
     * The item model this definition is for. If the model is in the {@code minecraft} namespace, then the definition must have at least one predicate.
     *
     * <p>If multiple item definitions for a model are registered, then only one can have no predicate.</p>
     */
    @NonNull Identifier model();

    /**
     * The icon used for this item.
     *
     * <p>If none is set in the item's Bedrock options, then the item's Bedrock identifier is used,
     * the namespace separator ({@code :}) replaced with {@code .} and the path separators ({@code /}) replaced with {@code _}. For example:</p>
     *
     * <p>{@code my_datapack:my_custom_item} => {@code my_datapack.my_custom_item}</p>
     * <p>{@code my_datapack:cool_items/cool_item_1} => {@code my_datapack.cool_items_cool_item_1}</p>
     */
    default @NonNull String icon() {
        return bedrockOptions().icon() == null ? bedrockIdentifier().toString().replaceAll(":", ".").replaceAll("/", "_") : bedrockOptions().icon();
    }

    /**
     * The predicates that have to match for this item definition to be used. These predicates are similar to the Java item model predicates.
     */
    @NonNull List<CustomItemPredicate> predicates();

    /**
     * The predicate strategy to be used. Determines if one of, or all of the predicates have to pass for this item definition to be used. Defaults to {@link PredicateStrategy#AND}.
     */
    PredicateStrategy predicateStrategy();

    /**
     * The priority of this definition. For all definitions for a single Java item model, definitions with a higher priority will be matched first. Defaults to 0.
     */
    int priority();

    /**
     * The item's Bedrock options. These describe item properties that can't be described in item components, e.g. item texture size and if the item is allowed in the off-hand.
     */
    @NonNull CustomItemBedrockOptions bedrockOptions();

    /**
     * The item's data components. It is expected that the item <em>always</em> has these components on the server. If the components mismatch, bugs will occur.
     *
     * <p>Currently, the following components are (somewhat) supported:</p>
     *
     * <ul>
     *     <li>{@code minecraft:consumable} ({@link DataComponent#CONSUMABLE})</li>
     *     <li>{@code minecraft:equippable} ({@link DataComponent#EQUIPPABLE})</li>
     *     <li>{@code minecraft:food} ({@link DataComponent#FOOD})</li>
     *     <li>{@code minecraft:max_damage} ({@link DataComponent#MAX_DAMAGE})</li>
     *     <li>{@code minecraft:max_stack_size} ({@link DataComponent#MAX_STACK_SIZE})</li>
     *     <li>{@code minecraft:use_cooldown} ({@link DataComponent#USE_COOLDOWN})</li>
     *     <li>{@code minecraft:enchantable} ({@link DataComponent#ENCHANTABLE})</li>
     *     <li>{@code minecraft:tool} ({@link DataComponent#TOOL})</li>
     *     <li>{@code minecraft:repairable} ({@link DataComponent#REPAIRABLE})</li>
     * </ul>
     *
     * <p>Note: some components, for example {@code minecraft:rarity}, {@code minecraft:enchantment_glint_override}, and {@code minecraft:attribute_modifiers} are translated automatically,
     * and do not have to be specified here.</p>
     *
     * @see DataComponent
     */
    @NonNull DataComponentMap components();

    static Builder builder(Identifier identifier, Identifier itemModel) {
        return GeyserApi.api().provider(Builder.class, identifier, itemModel);
    }

    interface Builder {

        Builder displayName(String displayName);

        Builder priority(int priority);

        Builder bedrockOptions(CustomItemBedrockOptions.@NonNull Builder options);

        Builder predicate(@NonNull CustomItemPredicate predicate);

        Builder predicateStrategy(@NonNull PredicateStrategy strategy);

        <T> Builder component(@NonNull DataComponent<T> component, @NonNull T value);

        CustomItemDefinition build();
    }
}
