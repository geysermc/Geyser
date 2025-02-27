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

package org.geysermc.geyser.api.pack;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.GeyserApi;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

/**
 * Represents a pack codec that can be used
 * to provide resource packs to clients.
 * @since 2.1.1
 */
public abstract class PackCodec {

    /**
     * Gets the sha256 hash of the resource pack.
     *
     * @return the hash of the resource pack
     * @since 2.1.1
     */
    public abstract byte @NonNull [] sha256();

    /**
     * Gets the resource pack size.
     *
     * @return the resource pack file size
     * @since 2.1.1
     */
    public abstract long size();

    /**
     * @deprecated use {@link #serialize()} instead.
     */
    @Deprecated
    @NonNull
    public SeekableByteChannel serialize(@NonNull ResourcePack resourcePack) throws IOException {
        return serialize();
    };

    /**
     * Serializes the given codec into a byte buffer.
     *
     * @return the serialized resource pack
     * @since 2.6.2
     */
    @NonNull
    public abstract SeekableByteChannel serialize() throws IOException;

    /**
     * Creates a new resource pack from this codec.
     *
     * @return the new resource pack
     * @since 2.1.1
     */
    @NonNull
    protected abstract ResourcePack create();

    /**
     * Creates a new resource pack builder from this codec.
     *
     * @return the new resource pack builder
     * @since 2.6.2
     */
    protected abstract ResourcePack.@NonNull Builder createBuilder();

    /**
     * Creates a new pack provider from the given path.
     *
     * @param path the path to create the pack provider from
     * @return the new pack provider
     * @since 2.1.1
     */
    @NonNull
    public static PathPackCodec path(@NonNull Path path) {
        return GeyserApi.api().provider(PathPackCodec.class, path);
    }

    /**
     * Creates a new pack provider from the given url.
     *
     * @param url the url to create the pack provider from
     * @return the new pack provider
     * @since 2.6.2
     */
    @NonNull
    public static UrlPackCodec url(@NonNull String url) {
        return GeyserApi.api().provider(UrlPackCodec.class, url);
    }
}
