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

package org.geysermc.geyser.entity;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.api.entity.EntityIdentifier;

import java.util.concurrent.atomic.AtomicInteger;

public record GeyserEntityIdentifier(NbtMap nbt) implements EntityIdentifier {
    private static final AtomicInteger RUNTIME_ID_ALLOCATOR = new AtomicInteger(100000);

    @Override
    public boolean hasSpawnEgg() {
        return this.nbt.getBoolean("hasspawnegg");
    }

    @NonNull
    @Override
    public String identifier() {
        return this.nbt.getString("id");
    }

    @Override
    public boolean isSummonable() {
        return this.nbt.getBoolean("summonable");
    }

    public static class EntityIdentifierBuilder implements EntityIdentifier.Builder {
        private final NbtMapBuilder nbt = NbtMap.builder();

        @Override
        public Builder spawnEgg(boolean spawnEgg) {
            this.nbt.putBoolean("hasspawnegg", spawnEgg);
            return this;
        }

        @Override
        public Builder identifier(String identifier) {
            this.nbt.putString("id", identifier);
            return this;
        }

        @Override
        public Builder summonable(boolean summonable) {
            this.nbt.putBoolean("summonable", summonable);
            return this;
        }

        @Override
        public EntityIdentifier build() {
            // Vanilla registry information
            this.nbt.putString("bid", "");
            this.nbt.putInt("rid", RUNTIME_ID_ALLOCATOR.getAndIncrement());
            this.nbt.putBoolean("experimental", false);

            return new GeyserEntityIdentifier(this.nbt.build());
        }
    }
}