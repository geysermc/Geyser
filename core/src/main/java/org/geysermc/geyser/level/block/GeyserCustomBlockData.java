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

package org.geysermc.geyser.level.block;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.property.CustomBlockProperty;
import org.geysermc.geyser.api.block.custom.property.PropertyType;
import org.geysermc.geyser.api.util.CreativeCategory;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class GeyserCustomBlockData implements CustomBlockData {
    private final String name;
    private final boolean includedInCreativeInventory;
    private final CreativeCategory creativeCategory;
    private final String creativeGroup;
    private final CustomBlockComponents components;
    private final Map<String, CustomBlockProperty<?>> properties;
    private final List<CustomBlockPermutation> permutations;

    private final Map<String, Object> defaultProperties;

    GeyserCustomBlockData(Builder builder) {
        this.name = builder.name;
        if (name == null) {
            throw new IllegalStateException("Name must be set");
        }

        this.includedInCreativeInventory = builder.includedInCreativeInventory;
        this.creativeCategory = builder.creativeCategory;
        this.creativeGroup = builder.creativeGroup;

        this.components = builder.components;

        if (!builder.properties.isEmpty()) {
            this.properties = Object2ObjectMaps.unmodifiable(new Object2ObjectArrayMap<>(builder.properties));
            Object2ObjectMap<String, Object> defaultProperties = new Object2ObjectOpenHashMap<>(this.properties.size());
            for (CustomBlockProperty<?> property : properties.values()) {
                if (property.values().size() > 16) {
                    GeyserLogger.get().warning(property.name() + " contains more than 16 values, but BDS specifies it should not. This may break in future versions.");
                }
                if (property.values().stream().distinct().count() != property.values().size()) {
                    throw new IllegalStateException(property.name() + " has duplicate values.");
                }
                if (property.values().isEmpty()) {
                    throw new IllegalStateException(property.name() + " contains no values.");
                }
                defaultProperties.put(property.name(), property.values().get(0));
            }
            this.defaultProperties = Object2ObjectMaps.unmodifiable(defaultProperties);
        } else {
            this.properties = Object2ObjectMaps.emptyMap();
            this.defaultProperties = Object2ObjectMaps.emptyMap();
        }

        if (!builder.permutations.isEmpty()) {
            this.permutations = List.of(builder.permutations.toArray(new CustomBlockPermutation[0]));
        } else {
            this.permutations = ObjectLists.emptyList();
        }
    }

    @Override
    public @NonNull String name() {
        return name;
    }

    @Override
    public @NonNull String identifier() {
        return Constants.GEYSER_CUSTOM_NAMESPACE + ":" + name;
    }

    @Override
    public boolean includedInCreativeInventory() {
        return includedInCreativeInventory;
    }

    @Override
    public @Nullable CreativeCategory creativeCategory() {
        return creativeCategory;
    }

    @Override
    public @Nullable String creativeGroup() {
        return creativeGroup;
    }

    @Override
    public CustomBlockComponents components() {
        return components;
    }

    @Override
    public @NonNull Map<String, CustomBlockProperty<?>> properties() {
        return properties;
    }

    @Override
    public @NonNull List<CustomBlockPermutation> permutations() {
        return permutations;
    }

    @Override
    public @NonNull CustomBlockState defaultBlockState() {
        return new GeyserCustomBlockState(this, defaultProperties);
    }

    @Override
    public CustomBlockState.@NonNull Builder blockStateBuilder() {
        return new GeyserCustomBlockState.Builder(this);
    }

    public static class Builder implements CustomBlockData.Builder {
        private String name;
        private boolean includedInCreativeInventory;
        private CreativeCategory creativeCategory;
        private String creativeGroup;
        private CustomBlockComponents components;
        private final Object2ObjectMap<String, CustomBlockProperty<?>> properties = new Object2ObjectOpenHashMap<>();
        private List<CustomBlockPermutation> permutations = ObjectLists.emptyList();

        @Override
        public Builder name(@NonNull String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder includedInCreativeInventory(boolean includedInCreativeInventory) {
            this.includedInCreativeInventory = includedInCreativeInventory;
            return this;
        }

        @Override
        public Builder creativeCategory(@Nullable CreativeCategory creativeCategory) {
            this.creativeCategory = creativeCategory;
            return this;
        }

        @Override
        public Builder creativeGroup(@Nullable String creativeGroup) {
            this.creativeGroup = creativeGroup;
            return this;
        }

        @Override
        public Builder components(@NonNull CustomBlockComponents components) {
            this.components = components;
            return this;
        }

        @Override
        public Builder booleanProperty(@NonNull String propertyName) {
            this.properties.put(propertyName, new GeyserCustomBlockProperty<>(propertyName, List.of((byte) 0, (byte) 1), PropertyType.booleanProp()));
            return this;
        }

        @Override
        public Builder intProperty(@NonNull String propertyName, List<Integer> values) {
            this.properties.put(propertyName, new GeyserCustomBlockProperty<>(propertyName, values, PropertyType.integerProp()));
            return this;
        }

        @Override
        public Builder stringProperty(@NonNull String propertyName, List<String> values) {
            this.properties.put(propertyName, new GeyserCustomBlockProperty<>(propertyName, values, PropertyType.stringProp()));
            return this;
        }

        @Override
        public Builder permutations(@NonNull List<CustomBlockPermutation> permutations) {
            this.permutations = permutations;
            return this;
        }

        @Override
        public CustomBlockData build() {
            return new GeyserCustomBlockData(this);
        }
    }
}
