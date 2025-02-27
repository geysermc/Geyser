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

package org.geysermc.geyser.pack.option;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.PriorityOption;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;
import org.geysermc.geyser.pack.GeyserResourcePack;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OptionHolder extends HashMap<ResourcePackOption.Type, ResourcePackOption<?>> {

    public OptionHolder() {
        super();
    }

    // Used when adding resource packs initially to ensure that a priority option is always set
    // It is however NOT used for session-options, as then the "normal" prio might override
    // the resource pack option
    public OptionHolder(PriorityOption option) {
        super();
        put(option.type(), option);
    }

    public void validateAndAdd(ResourcePack pack, ResourcePackOption<?>... options) {
        for (ResourcePackOption<?> option : options) {
            // Validate before adding
            option.validate(pack);

            // Ensure that we do not have duplicate types.
            if (super.containsKey(option.type())) {
                super.replace(option.type(), option);
            } else {
                super.put(option.type(), option);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T valueOrFallback(ResourcePackOption.@NonNull Type type,
                                        @Nullable OptionHolder sessionPackOptions,
                                        @NonNull OptionHolder resourcePackOptions,
                                        @NonNull T defaultValue) {
        ResourcePackOption<?> option;

        // First: the session's options, if they exist
        if (sessionPackOptions != null) {
            option = sessionPackOptions.get(type);
            if (option != null) {
                return (T) option.value();
            }
        }

        // Second: check the resource pack options
        option = resourcePackOptions.get(type);
        if (option != null) {
            return (T) option.value();
        }

        // Finally: return default
        return defaultValue;
    }

    public static @Nullable ResourcePackOption<?> optionByType(ResourcePackOption.@NonNull Type type,
                                                     @Nullable OptionHolder sessionPackOptions,
                                                     @NonNull OptionHolder resourcePackOptions) {

        // First: the session-specific options, if these exist
        if (sessionPackOptions != null) {
            ResourcePackOption<?> option = sessionPackOptions.get(type);
            if (option != null) {
                return option;
            }
        }

        // Second: check the default holder for the option, if it exists;
        // Or return null if the option isn't set.
        return resourcePackOptions.get(type);
    }

    public void remove(ResourcePackOption<?> option) {
        super.remove(option.type());
    }

    /**
     * @return the options of this holder in an immutable collection
     */
    public Collection<ResourcePackOption<?>> immutableValues() {
        return Collections.unmodifiableCollection(values());
    }

    /**
     * @return the options of this option holder, with fallbacks to options of a {@link GeyserResourcePack}
     * if they're not already overridden here
     */
    public Collection<ResourcePackOption<?>> immutableValues(OptionHolder defaultValues) {
        // Create a map to hold the combined values
        Map<ResourcePackOption.Type, ResourcePackOption<?>> combinedOptions = new HashMap<>(this);

        // Add options from the pack if not already overridden by this OptionHolder
        defaultValues.forEach(combinedOptions::putIfAbsent);

        // Return an immutable collection of the combined options
        return Collections.unmodifiableCollection(combinedOptions.values());
    }
}
