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

package org.geysermc.geyser.registry.mappings.components.readers;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.item.custom.v2.component.DataComponent;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.components.DataComponentReader;
import org.geysermc.geyser.registry.mappings.util.MappingsUtil;
import org.geysermc.geyser.registry.mappings.util.NodeReader;

/**
 * For some reason, Minecraft Java uses
 *
 * <pre>
 *     {@code
 *     {
 *       "minecraft:enchantable: {
 *         "value": 4
 *       }
 *     }
 *     }
 * </pre>
 *
 * instead of
 *
 * <pre>
 *     {@code
 *     {
 *         "minecraft:enchantable": 4
 *     }
 *     }
 * </pre>
 *
 * This reader allows both styles.
 */
public class EnchantableReader extends DataComponentReader<Integer> {

    public EnchantableReader() {
        super(DataComponent.ENCHANTABLE);
    }

    @Override
    protected Integer readDataComponent(@NonNull JsonElement element, String... context) throws InvalidCustomMappingsFileException {
        try {
            if (element instanceof JsonPrimitive primitive) {
                return NodeReader.NON_NEGATIVE_INT.read(primitive, "reading component", context);
            }
        } catch (InvalidCustomMappingsFileException ignored) {}
        return MappingsUtil.readOrThrow(element, "value", NodeReader.NON_NEGATIVE_INT);
    }
}
