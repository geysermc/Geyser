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

package org.geysermc.geyser.api.item.custom.v2.predicate;

/**
 * A predicate that checks for a certain boolean property of the item stack and returns true if it matches the expected value.
 *
 * @param property the property to check.
 * @param expected whether the property should be true or false. Defaults to true.
 * @param index only used for the {@code CUSTOM_MODEL_DATA} property, determines which flag of the item's custom model data to check. Defaults to 0.
 */
public record ConditionPredicate(ConditionProperty property, boolean expected, int index) implements CustomItemPredicate {

    public ConditionPredicate(ConditionProperty property, boolean expected) {
        this(property, expected, 0);
    }

    public ConditionPredicate(ConditionProperty property) {
        this(property, true);
    }

    public enum ConditionProperty {
        /**
         * Checks if the item is broken (has 1 durability point left).
         */
        BROKEN,
        /**
         * Checks if the item is damaged (has non-full durability).
         */
        DAMAGED,
        /**
         * Checks if the item is unbreakable.
         */
        UNBREAKABLE,
        /**
         * Returns one of the item's custom model data flags, defaults to false.
         */
        CUSTOM_MODEL_DATA
    }
}
