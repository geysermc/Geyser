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
 * Represents the data sent over to a client regarding
 * an entity's identifier.
 */
public interface EntityIdentifier {

    /**
     * Gets whether this entity has a spawn egg or not.
     *
     * @return whether this entity has a spawn egg or not
     */
    boolean hasSpawnEgg();

    /**
     * Gets the entity's identifier that is sent to the client.
     *
     * @return the entity's identifier that is sent to the client.
     */
    @NonNull
    String identifier();

    /**
     * Gets whether the entity is summonable or not.
     *
     * @return whether the entity is summonable or not
     */
    boolean isSummonable();

    @NonNull
    static Builder builder() {
        return GeyserApi.api().provider(Builder.class);
    }

    interface Builder {

        /**
         * Sets whether the entity has a spawn egg or not.
         *
         * @param spawnEgg whether the entity has a spawn egg or not
         * @return the builder
         */
        Builder spawnEgg(boolean spawnEgg);

        /**
         * Sets the entity's identifier that is sent to the client.
         *
         * @param identifier the entity's identifier that is sent to the client
         * @return the builder
         */
        Builder identifier(String identifier);

        /**
         * Sets whether the entity is summonable or not.
         *
         * @param summonable whether the entity is summonable or not
         * @return the builder
         */
        Builder summonable(boolean summonable);

        /**
         * Builds the entity identifier.
         *
         * @return the entity identifier
         */
        EntityIdentifier build();
    }
}