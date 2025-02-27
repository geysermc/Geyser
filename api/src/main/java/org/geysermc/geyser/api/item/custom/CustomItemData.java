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

package org.geysermc.geyser.api.item.custom;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.predicate.CustomItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.RangeDispatchPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.condition.ConditionPredicateProperty;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.api.util.TriState;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is used to store data for a custom item.
 *
 * @deprecated use the new {@link org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition}
 */
@Deprecated
public interface CustomItemData {
    /**
     * Gets the item's name.
     *
     * @return the item's name
     */
    @NonNull String name();

    /**
     * Gets the custom item options of the item.
     *
     * @return the custom item options of the item.
     */
    CustomItemOptions customItemOptions();

    /**
     * Gets the item's display name. By default, this is the item's name.
     *
     * @return the item's display name
     */
    @NonNull String displayName();

    /**
     * Gets the item's icon. By default, this is the item's name.
     *
     * @return the item's icon
     */
    @NonNull String icon();

    /**
     * Gets if the item is allowed to be put into the offhand.
     *
     * @return true if the item is allowed to be used in the offhand, false otherwise
     */
    boolean allowOffhand();

    /**
     * Gets if the item should be displayed as handheld, like a tool.
     *
     * @return true if the item should be displayed as handheld, false otherwise
     */
    boolean displayHandheld();

    /**
     * Gets the item's creative category, or tab id.
     *
     * @return the item's creative category
     */
    @NonNull OptionalInt creativeCategory();

    /**
     * Gets the item's creative group.
     *
     * @return the item's creative group
     */
    @Nullable String creativeGroup();

    /**
     * Gets the item's texture size. This is to resize the item if the texture is not 16x16.
     *
     * @return the item's texture size
     */
    int textureSize();

    /**
     * Gets the item's render offsets. If it is null, the item will be rendered normally, with no offsets.
     *
     * @return the item's render offsets
     */
    @Nullable CustomRenderOffsets renderOffsets();

    /**
     * Gets the item's set of tags that can be used in Molang.
     * Equivalent to "tag:some_tag"
     *
     * @return the item's tags, if they exist
     */
    @NonNull Set<String> tags();

    static CustomItemData.Builder builder() {
        return GeyserApi.api().provider(CustomItemData.Builder.class);
    }

    default CustomItemDefinition.Builder toDefinition(Identifier javaItem) {
        // TODO non vanilla
        CustomItemDefinition.Builder definition = CustomItemDefinition.builder(javaItem, javaItem)
            .displayName(displayName())
            .bedrockOptions(CustomItemBedrockOptions.builder()
                .icon(icon())
                .allowOffhand(allowOffhand())
                .displayHandheld(displayHandheld())
                .creativeCategory(creativeCategory().isEmpty() ? CreativeCategory.NONE : CreativeCategory.values()[creativeCategory().getAsInt()])
                .creativeGroup(creativeGroup())
                .tags(tags().stream().map(Identifier::of).collect(Collectors.toSet()))
            );

        CustomItemOptions options = customItemOptions();
        if (options.customModelData().isPresent()) {
            definition.predicate(CustomItemPredicate.rangeDispatch(RangeDispatchPredicateProperty.CUSTOM_MODEL_DATA, options.customModelData().getAsInt()));
        }
        if (options.damagePredicate().isPresent()) {
            definition.predicate(CustomItemPredicate.rangeDispatch(RangeDispatchPredicateProperty.DAMAGE, options.damagePredicate().getAsInt()));
        }
        if (options.unbreakable() != TriState.NOT_SET) {
            definition.predicate(CustomItemPredicate.condition(ConditionPredicateProperty.HAS_COMPONENT,
                Objects.requireNonNull(options.unbreakable().toBoolean()), Identifier.of("minecraft", "unbreakable")));
        }
        return definition;
    }

    interface Builder {
        /**
         * Will also set the display name and icon to the provided parameter, if it is currently not set.
         */
        Builder name(@NonNull String name);

        Builder customItemOptions(@NonNull CustomItemOptions customItemOptions);

        Builder displayName(@NonNull String displayName);

        Builder icon(@NonNull String icon);

        Builder allowOffhand(boolean allowOffhand);

        Builder displayHandheld(boolean displayHandheld);

        Builder creativeCategory(int creativeCategory);

        Builder creativeGroup(@Nullable String creativeGroup);

        Builder textureSize(int textureSize);

        Builder renderOffsets(@Nullable CustomRenderOffsets renderOffsets);

        Builder tags(@Nullable Set<String> tags);

        CustomItemData build();
    }
}
