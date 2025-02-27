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

package org.geysermc.geyser.session.cache.tags;

import it.unimi.dsi.fastutil.ints.IntArrays;
import lombok.Data;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.TagCache;
import org.geysermc.geyser.session.cache.registry.JavaRegistryKey;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Similar to vanilla Minecraft's HolderSets, stores either a tag or a list of IDs (this list can also be represented as a single ID in vanilla HolderSets).
 *
 * <p>Because HolderSets utilise tags, when loading a HolderSet, Geyser must store tags for the registry the HolderSet is for (see {@link JavaRegistryKey}).</p>
 *
 * <p>Use the {@link GeyserHolderSet#readHolderSet} method to easily read a HolderSet from NBT sent by a server. To turn the HolderSet into a list of network IDs, use the {@link GeyserHolderSet#resolveRaw} method.
 * To turn the HolderSet into a list of objects, use the {@link GeyserHolderSet#resolve} method.</p>
 */
@Data
public final class GeyserHolderSet<T> {

    private final JavaRegistryKey<T> registry;
    private final @Nullable Tag<T> tag;
    private final int @Nullable [] holders;

    public GeyserHolderSet(JavaRegistryKey<T> registry, int @NonNull [] holders) {
        this(registry, null, holders);
    }

    public GeyserHolderSet(JavaRegistryKey<T> registry, @NonNull Tag<T> tagId) {
        this(registry, tagId, null);
    }

    private GeyserHolderSet(JavaRegistryKey<T> registry, @Nullable Tag<T> tag, int @Nullable [] holders) {
        this.registry = registry;
        this.tag = tag;
        this.holders = holders;
    }

    /**
     * Constructs a {@link GeyserHolderSet} from a MCPL HolderSet.
     */
    public static <T> GeyserHolderSet<T> fromHolderSet(JavaRegistryKey<T> registry, @NonNull HolderSet holderSet) {
        return new GeyserHolderSet<>(registry, new Tag<>(registry, holderSet.getLocation()), holderSet.getHolders());
    }

    /**
     * Resolves the HolderSet, and automatically maps the network IDs to their respective object types. If the HolderSet is a list of IDs, this will be returned. If it is a tag, the tag will be resolved from the tag cache.
     *
     * @return the HolderSet turned into a list of objects.
     */
    public List<T> resolve(GeyserSession session) {
        return TagCache.mapRawArray(session, resolveRaw(session.getTagCache()), registry);
    }

    /**
     * Resolves the HolderSet. If the HolderSet is a list of IDs, this will be returned. If it is a tag, the tag will be resolved from the tag cache.
     *
     * @return the HolderSet turned into a list of objects.
     */
    public int[] resolveRaw(TagCache tagCache) {
        if (holders != null) {
            return holders;
        }

        return tagCache.getRaw(Objects.requireNonNull(tag, "HolderSet must have a tag if it doesn't have a list of IDs"));
    }

    /**
     * Reads a HolderSet from an object from NBT.
     *
     * @param session session, only used for logging purposes.
     * @param registry the registry the HolderSet contains IDs from.
     * @param holderSet the HolderSet as an object from NBT.
     * @param keyIdMapping a function that maps resource location IDs in the HolderSet's registry to their network IDs.
     */
    public static <T> GeyserHolderSet<T> readHolderSet(GeyserSession session, JavaRegistryKey<T> registry, @Nullable Object holderSet, ToIntFunction<Key> keyIdMapping) {
        if (holderSet == null) {
            return new GeyserHolderSet<>(registry, IntArrays.EMPTY_ARRAY);
        }

        if (holderSet instanceof String stringTag) {
            if (stringTag.startsWith("#")) {
                // Tag
                return new GeyserHolderSet<>(registry, new Tag<>(registry, Key.key(stringTag.substring(1)))); // Remove '#' at beginning that indicates tag
            } else if (stringTag.isEmpty()) {
                return new GeyserHolderSet<>(registry, IntArrays.EMPTY_ARRAY);
            }
            return new GeyserHolderSet<>(registry, new int[]{keyIdMapping.applyAsInt(Key.key(stringTag))});
        } else if (holderSet instanceof List<?> list) {
            // Assume the list is a list of strings
            return new GeyserHolderSet<>(registry, list.stream().map(o -> (String) o).map(Key::key).mapToInt(keyIdMapping).toArray());
        }
        GeyserLogger.get().warning("Failed parsing HolderSet for registry + " + registry + "! Expected either a tag, a string ID or a list of string IDs, found " + holderSet);
        return new GeyserHolderSet<>(registry, IntArrays.EMPTY_ARRAY);
    }
}
