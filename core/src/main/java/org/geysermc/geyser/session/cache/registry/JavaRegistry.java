/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.session.cache.registry;

import org.checkerframework.checker.index.qual.NonNegative;

import java.util.List;

/**
 * A wrapper for a list, holding Java registry values.
 */
public interface JavaRegistry<T> {

    /**
     * Looks up a registry entry by its ID. The object can be null, or not present.
     */
    T byId(@NonNegative int id);

    /**
     * Looks up a registry entry by its ID, and returns it wrapped in {@link RegistryEntryData} so that its registered key is also known. The object can be null, or not present.
     */
    RegistryEntryData<T> entryById(@NonNegative int id);

    /**
     * Reverse looks-up an object to return its network ID, or -1.
     */
    int byValue(T value);

    /**
     * Reverse looks-up an object to return it wrapped in {@link RegistryEntryData}, or null.
     */
    RegistryEntryData<T> entryByValue(T value);

    /**
     * Resets the objects by these IDs.
     */
    void reset(List<RegistryEntryData<T>> values);

    /**
     * All values of this registry, as a list.
     */
    List<T> values();
}
