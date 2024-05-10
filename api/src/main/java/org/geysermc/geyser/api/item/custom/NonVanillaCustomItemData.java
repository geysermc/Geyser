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

package org.geysermc.geyser.api.item.custom;

import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.GeyserApi;

import java.util.Set;

/**
 * Represents a completely custom item that is not based on an existing vanilla Minecraft item.
 */
public interface NonVanillaCustomItemData extends CustomItemData {
    /**
     * Gets the java identifier for this item.
     *
     * @return The java identifier for this item.
     */
    @NonNull String identifier();

    /**
     * Gets the java item id of the item.
     *
     * @return the java item id of the item
     */
    @NonNegative int javaId();

    /**
     * Gets the armor type of the item.
     *
     * @return the armor type of the item
     */
    @Nullable String armorType();

    /**
     * Gets the repair materials of the item.
     *
     * @return the repair materials of the item
     */
    @Nullable Set<String> repairMaterials();

    /**
     * Gets if the item is a hat. This is used to determine if the item should be rendered on the player's head, and
     * normally allow the player to equip it. This is not meant for armor.
     *
     * @return if the item is a hat
     */
    boolean isHat();

    /**
     * Gets if the item is chargable, like a bow.
     *
     * @return if the item should act like a chargable item
     */
    boolean isChargeable();

    /**
     * Gets the block the item places.
     *
     * @return the block the item places
     */
    String block();

    static NonVanillaCustomItemData.Builder builder() {
        return GeyserApi.api().provider(NonVanillaCustomItemData.Builder.class);
    }

    interface Builder extends CustomItemData.Builder {
        @Override
        Builder name(@NonNull String name);

        Builder identifier(@NonNull String identifier);

        Builder javaId(@NonNegative int javaId);

        Builder armorType(@Nullable String armorType);

        Builder repairMaterials(@Nullable Set<String> repairMaterials);

        Builder hat(boolean isHat);

        Builder chargeable(boolean isChargeable);

        Builder block(String block);

        NonVanillaCustomItemData build();
    }
}
