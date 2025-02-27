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

package org.geysermc.geyser.api.item.custom.v2.component;

import org.checkerframework.checker.index.qual.Positive;

public record Consumable(@Positive float consumeSeconds, Animation animation) {

    public Consumable {
        if (consumeSeconds <= 0.0F) {
            throw new IllegalArgumentException("Consume seconds must be above 0");
        }
    }

    /**
     * Not all animations work perfectly on bedrock. Bedrock behaviour is noted per animation. The {@code toot_horn} animation doesn't exist on bedrock, and is therefore not listed here.
     */
    public enum Animation {
        /**
         * Does nothing in 1st person, appears as eating in 3rd person.
         */
        NONE,
        /**
         * Appears to look correctly.
         */
        EAT,
        /**
         * Appears to look correctly.
         */
        DRINK,
        /**
         * Does nothing in 1st person, eating in 3rd person.
         */
        BLOCK,
        /**
         * Does nothing in 1st person, eating in 3rd person.
         */
        BOW,
        /**
         * Does nothing in 1st person, but looks like spear in 3rd person.
         */
        SPEAR,
        /**
         * Does nothing in 1st person, eating in 3rd person.
         */
        CROSSBOW,
        /**
         * Does nothing in 1st person, but looks like spyglass in 3rd person.
         */
        SPYGLASS,
        /**
         * Brush in 1st and 3rd person. Will look weird when not displayed handheld.
         */
        BRUSH
    }
}
