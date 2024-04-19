/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.api.entity;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.GeyserApi;

/**
 * Holds information about an entity that remains constant no matter
 * its properties. This is typically data such as its identifier,
 * its height/width, offset, etc.
 */
public interface EntityDefinition {

    /**
     * Gets the identifier of this entity.
     *
     * @return the identifier of this entity
     */
    @NonNull
    EntityIdentifier entityIdentifier();

    /**
     * Gets the width of this entity.
     *
     * @return the width of this entity
     */
    float width();

    /**
     * Gets the height of this entity.
     *
     * @return the height of this entity
     */
    float height();

    /**
     * Gets the offset of this entity.
     *
     * @return the offset of this entity
     */
    float offset();

    static Builder builder() {
        return GeyserApi.api().provider(Builder.class);
    }

    interface Builder {

        /**
         * Sets the identifier of this entity.
         *
         * @param identifier the identifier of this entity
         * @return the builder
         */
        Builder identifier(@NonNull EntityIdentifier identifier);

        /**
         * Sets the width of this entity.
         *
         * @param width the width of this entity
         * @return the builder
         */
        Builder width(float width);

        /**
         * Sets the height of this entity.
         *
         * @param height the height of this entity
         * @return the builder
         */
        Builder height(float height);

        /**
         * Sets the offset of this entity.
         *
         * @param offset the offset of this entity
         * @return the builder
         */
        Builder offset(float offset);

        /**
         * Builds the entity definition.
         *
         * @return the entity definition
         */
        EntityDefinition build();
    }
}