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

package org.geysermc.geyser.registry.mappings.components.readers;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.item.custom.v2.component.DataComponent;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.components.DataComponentReader;
import org.geysermc.geyser.registry.mappings.util.NodeReader;

public class IntComponentReader extends DataComponentReader<Integer> {
    private final int minimum;
    private final int maximum;

    public IntComponentReader(DataComponent<Integer> type, int minimum, int maximum) {
        super(type);
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public IntComponentReader(DataComponent<Integer> type, int minimum) {
        this(type, minimum, Integer.MAX_VALUE);
    }

    @Override
    protected Integer readDataComponent(@NonNull JsonElement element, String... context) throws InvalidCustomMappingsFileException {
        if (!element.isJsonPrimitive()) {
            throw new InvalidCustomMappingsFileException("reading component", "value must be a primitive", context);
        }
        return NodeReader.boundedInt(minimum, maximum).read((JsonPrimitive) element, "reading component", context);
    }
}
