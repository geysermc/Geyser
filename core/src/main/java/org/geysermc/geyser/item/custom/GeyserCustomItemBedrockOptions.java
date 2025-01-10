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

package org.geysermc.geyser.item.custom;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.item.custom.CustomRenderOffsets;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.util.CreativeCategory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record GeyserCustomItemBedrockOptions(@Nullable String icon, boolean allowOffhand, boolean displayHandheld, @NonNull CreativeCategory creativeCategory, @Nullable String creativeGroup,
                                             int textureSize, @Nullable CustomRenderOffsets renderOffsets, @NonNull Set<String> tags) implements CustomItemBedrockOptions {

    public static class Builder implements CustomItemBedrockOptions.Builder {
        private String icon = null;
        private boolean allowOffhand = true;
        private boolean displayHandheld = false;
        private CreativeCategory creativeCategory = CreativeCategory.NONE;
        private String creativeGroup = null;
        private int textureSize = 16;
        private CustomRenderOffsets renderOffsets = null;
        private Set<String> tags = new HashSet<>();

        @Override
        public Builder icon(@Nullable String icon) {
            this.icon = icon;
            return this;
        }

        @Override
        public Builder allowOffhand(boolean allowOffhand) {
            this.allowOffhand = allowOffhand;
            return this;
        }

        @Override
        public Builder displayHandheld(boolean displayHandheld) {
            this.displayHandheld = displayHandheld;
            return this;
        }

        @Override
        public Builder creativeCategory(CreativeCategory creativeCategory) {
            this.creativeCategory = creativeCategory;
            return this;
        }

        @Override
        public Builder creativeGroup(@Nullable String creativeGroup) {
            this.creativeGroup = creativeGroup;
            return this;
        }

        @Override
        @Deprecated
        public Builder textureSize(int textureSize) {
            this.textureSize = textureSize;
            return this;
        }

        @Override
        @Deprecated
        public Builder renderOffsets(@Nullable CustomRenderOffsets renderOffsets) {
            this.renderOffsets = renderOffsets;
            return this;
        }

        @Override
        public Builder tags(@Nullable Set<String> tags) {
            this.tags = Objects.requireNonNullElseGet(tags, Set::of);
            return this;
        }

        @Override
        public CustomItemBedrockOptions build() {
            return new GeyserCustomItemBedrockOptions(icon, allowOffhand, displayHandheld, creativeCategory, creativeGroup, textureSize, renderOffsets, tags);
        }
    }
}