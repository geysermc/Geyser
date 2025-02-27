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

package org.geysermc.geyser.pack.url;

import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.pack.PathPackCodec;
import org.geysermc.geyser.api.pack.UrlPackCodec;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.registry.loader.ResourcePackLoader;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

public class GeyserUrlPackCodec extends UrlPackCodec {
    private final @NonNull String url;
    @Getter
    private PathPackCodec fallback;

    public GeyserUrlPackCodec(@NonNull String url) throws IllegalArgumentException {
        Objects.requireNonNull(url);
        this.url = url;
    }

    @Override
    public byte @NonNull [] sha256() {
        Objects.requireNonNull(fallback, "must call #create() before attempting to get the sha256!");
        return fallback.sha256();
    }

    @Override
    public long size() {
        Objects.requireNonNull(fallback, "must call #create() before attempting to get the size!");
        return fallback.size();
    }

    @Override
    public @NonNull SeekableByteChannel serialize() throws IOException {
        Objects.requireNonNull(fallback, "must call #create() before attempting to serialize!!");
        return fallback.serialize();
    }

    @Override
    @NonNull
    public GeyserResourcePack create() {
        return createBuilder().build();
    }

    @Override
    protected GeyserResourcePack.@NonNull Builder createBuilder() {
        if (this.fallback == null) {
            try {
                ResourcePackLoader.downloadPack(url, false).whenComplete((pack, throwable) -> {
                    if (throwable != null) {
                        throw new IllegalArgumentException(throwable);
                    } else if (pack != null) {
                        this.fallback = pack;
                    }
                }).join(); // Needed to ensure that we don't attempt to read a pack before downloading/checking it
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to download pack from the url %s (%s)!".formatted(url, e.getMessage()));
            }
        }

        return ResourcePackLoader.readPack(this);
    }

    @Override
    public @NonNull String url() {
        return this.url;
    }
}
