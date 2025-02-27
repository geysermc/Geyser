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

package org.geysermc.geyser.api.util;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the creative menu categories or tabs.
 */
public enum CreativeCategory {
    ALL("all", 0),
    CONSTRUCTION("construction", 1),
    NATURE("nature", 2),
    EQUIPMENT("equipment", 3),
    ITEMS("items", 4),
    ITEM_COMMAND_ONLY("item_command_only", 5),
    NONE("none", 6);

    private final String bedrockName;
    private final int id;

    CreativeCategory(String bedrockName, int id) {
        this.bedrockName = bedrockName;
        this.id = id;
    }

    /**
     * Gets the bedrock name (used in behaviour packs) of the category.
     * 
     * @return the name of the category
     */
    public @NonNull String bedrockName() {
        return bedrockName;
    }

    /**
     * Gets the internal ID of the category.
     * 
     * @return the ID of the category
     */
    public int id() {
        return id;
    }

    /**
     * Gets the creative category from its bedrock name.
     *
     * @return the creative category, or null if not found.
     */
    public static @Nullable CreativeCategory fromName(String name) {
        for (CreativeCategory category : values()) {
            if (category.bedrockName.equals(name)) {
                return category;
            }
        }
        return null;
    }
}
