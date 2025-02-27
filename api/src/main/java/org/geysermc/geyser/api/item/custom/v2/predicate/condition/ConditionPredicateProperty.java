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

package org.geysermc.geyser.api.item.custom.v2.predicate.condition;

import org.geysermc.geyser.api.util.Identifier;

public final class ConditionPredicateProperty<T> {

    /**
     * Checks if the item is broken (has 1 durability point left).
     */
    public static final ConditionPredicateProperty<Void> BROKEN = createNoData();
    /**
     * Checks if the item is damaged (has non-full durability).
     */
    public static final ConditionPredicateProperty<Void> DAMAGED = createNoData();
    /**
     * Returns one of the item's custom model data flags, defaults to false. Data in the predicate is an integer that sets the index of the flags to check.
     */
    public static final ConditionPredicateProperty<Integer> CUSTOM_MODEL_DATA = create();
    /**
     * Returns true if the item stack has a component with the identifier set in the predicate data.
     */
    public static final ConditionPredicateProperty<Identifier> HAS_COMPONENT = create();

    public final boolean requiresData;

    private ConditionPredicateProperty(boolean requiresData) {
        this.requiresData = requiresData;
    }

    private static <T> ConditionPredicateProperty<T> create() {
        return new ConditionPredicateProperty<>(true);
    }

    private static ConditionPredicateProperty<Void> createNoData() {
        return new ConditionPredicateProperty<>(false);
    }
}
