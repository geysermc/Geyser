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

package org.geysermc.geyser.registry.mappings.components;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.DataComponent;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.components.readers.ConsumableReader;
import org.geysermc.geyser.registry.mappings.components.readers.EnchantableReader;
import org.geysermc.geyser.registry.mappings.components.readers.EquippableReader;
import org.geysermc.geyser.registry.mappings.components.readers.FoodPropertiesReader;
import org.geysermc.geyser.registry.mappings.components.readers.IntComponentReader;
import org.geysermc.geyser.registry.mappings.components.readers.RepairableReader;
import org.geysermc.geyser.registry.mappings.components.readers.ToolPropertiesReader;
import org.geysermc.geyser.registry.mappings.components.readers.UseCooldownReader;
import org.geysermc.geyser.util.MinecraftKey;

import java.util.HashMap;
import java.util.Map;

public class DataComponentReaders {
    private static final Map<Key, DataComponentReader<?>> READERS = new HashMap<>();

    public static void readDataComponent(CustomItemDefinition.Builder builder, Key key, @NonNull JsonElement element, String baseContext) throws InvalidCustomMappingsFileException {
        DataComponentReader<?> reader = READERS.get(key);
        if (reader == null) {
            throw new InvalidCustomMappingsFileException("reading data components", "unknown data component " + key, baseContext);
        }
        reader.read(builder, element, "component " + key, baseContext);
    }

    static {
        READERS.put(MinecraftKey.key("consumable"), new ConsumableReader());
        READERS.put(MinecraftKey.key("equippable"), new EquippableReader());
        READERS.put(MinecraftKey.key("food"), new FoodPropertiesReader());
        READERS.put(MinecraftKey.key("max_damage"), new IntComponentReader(DataComponent.MAX_DAMAGE, 0));
        READERS.put(MinecraftKey.key("max_stack_size"), new IntComponentReader(DataComponent.MAX_STACK_SIZE, 1, 99));
        READERS.put(MinecraftKey.key("use_cooldown"), new UseCooldownReader());
        READERS.put(MinecraftKey.key("enchantable"), new EnchantableReader());
        READERS.put(MinecraftKey.key("tool"), new ToolPropertiesReader());
        READERS.put(MinecraftKey.key("repairable"), new RepairableReader());
    }
}
