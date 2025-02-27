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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.checkerframework.checker.index.qual.NonNegative;

import java.util.List;

public class SimpleJavaRegistry<T> implements JavaRegistry<T> {
    protected final ObjectArrayList<RegistryEntryData<T>> values = new ObjectArrayList<>();

    @Override
    public T byId(@NonNegative int id) {
        if (id < 0 || id >= this.values.size()) {
            return null;
        }
        return this.values.get(id).data();
    }

    @Override
    public RegistryEntryData<T> entryById(@NonNegative int id) {
        if (id < 0 || id >= this.values.size()) {
            return null;
        }
        return this.values.get(id);
    }

    @Override
    public int byValue(T value) {
        for (int i = 0; i < this.values.size(); i++) {
            if (values.get(i).data().equals(value)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public RegistryEntryData<T> entryByValue(T value) {
        for (RegistryEntryData<T> entry : this.values) {
            if (entry.data().equals(value)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void reset(List<RegistryEntryData<T>> values) {
        this.values.clear();
        this.values.addAll(values);
        this.values.trim();
    }

    @Override
    public List<T> values() {
        return this.values.stream().map(RegistryEntryData::data).toList();
    }

    @Override
    public String toString() {
        return this.values.toString();
    }
}
