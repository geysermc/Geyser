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

package org.geysermc.geyser.api.item.custom.v2.predicate.match;

import org.geysermc.geyser.api.util.Identifier;

public final class MatchPredicateProperty<T> {

    /**
     * Matches for the item's charged projectile. Usually used with crossbows, but checks any item with the {@code minecraft:charged_projectiles} component.
     */
    public static final MatchPredicateProperty<ChargeType> CHARGE_TYPE = create();
    /**
     * Matches the item's trim material identifier. Works for any item with the {@code minecraft:trim} component.
     */
    public static final MatchPredicateProperty<Identifier> TRIM_MATERIAL = create();
    /**
     * Matches the dimension identifier the Bedrock session player is currently in.
     */
    public static final MatchPredicateProperty<Identifier> CONTEXT_DIMENSION = create();
    /**
     * Matches a string of the item's custom model data strings.
     */
    public static final MatchPredicateProperty<CustomModelDataString> CUSTOM_MODEL_DATA = create();

    private MatchPredicateProperty() {}

    private static <T> MatchPredicateProperty<T> create() {
        return new MatchPredicateProperty<>();
    }
}
