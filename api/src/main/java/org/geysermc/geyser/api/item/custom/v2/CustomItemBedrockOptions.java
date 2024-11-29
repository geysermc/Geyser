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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.item.custom.CustomRenderOffsets;

import java.util.OptionalInt;
import java.util.Set;

/**
 * This is used to store options for a custom item that can't be described using item components.
 */
public interface CustomItemBedrockOptions {

    /**
     * Gets the item's icon. When not present, {@code <item model namespace>.<item model path>} is used.
     *
     * @return the item's icon
     */
    @Nullable
    String icon();

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
    @NonNull
    OptionalInt creativeCategory();

    /**
     * Gets the item's creative group.
     *
     * @return the item's creative group
     */
    @Nullable
    String creativeGroup();

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
    @Nullable
    CustomRenderOffsets renderOffsets();

    /**
     * Gets the item's set of tags that can be used in Molang.
     * Equivalent to "tag:some_tag"
     *
     * @return the item's tags, if they exist
     */
    @NonNull
    Set<String> tags();

    static Builder builder() {
        return GeyserApi.api().provider(Builder.class);
    }

    interface Builder {

        Builder icon(@Nullable String icon);

        Builder allowOffhand(boolean allowOffhand);

        Builder displayHandheld(boolean displayHandheld);

        Builder creativeCategory(int creativeCategory);

        Builder creativeGroup(@Nullable String creativeGroup);

        Builder textureSize(int textureSize);

        Builder renderOffsets(@Nullable CustomRenderOffsets renderOffsets);

        Builder tags(@Nullable Set<String> tags);

        CustomItemBedrockOptions build();
    }
}
