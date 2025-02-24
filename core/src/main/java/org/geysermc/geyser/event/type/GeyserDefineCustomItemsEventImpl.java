/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.event.type;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.exception.CustomItemDefinitionRegisterException;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class GeyserDefineCustomItemsEventImpl implements GeyserDefineCustomItemsEvent {
    private final Multimap<String, CustomItemData> deprecatedCustomItems = MultimapBuilder.hashKeys().arrayListValues().build();
    private final Multimap<Identifier, CustomItemDefinition> customItems;
    private final List<NonVanillaCustomItemData> nonVanillaCustomItems;

    public GeyserDefineCustomItemsEventImpl(Multimap<Identifier, CustomItemDefinition> customItems, List<NonVanillaCustomItemData> nonVanillaCustomItems) {
        this.customItems = customItems;
        this.nonVanillaCustomItems = nonVanillaCustomItems;
    }

    @Override
    @Deprecated
    public @NonNull Map<String, Collection<CustomItemData>> getExistingCustomItems() {
        return Collections.unmodifiableMap(deprecatedCustomItems.asMap());
    }

    @Override
    public @NonNull Map<Identifier, Collection<CustomItemDefinition>> customItemDefinitions() {
        return Collections.unmodifiableMap(customItems.asMap());
    }

    @Override
    public @NonNull List<NonVanillaCustomItemData> getExistingNonVanillaCustomItems() {
        return Collections.unmodifiableList(this.nonVanillaCustomItems);
    }

    @Override
    @Deprecated
    public boolean register(@NonNull String identifier, @NonNull CustomItemData customItemData) {
        try {
            Identifier vanillaItemIdentifier = Identifier.of(identifier);
            register(vanillaItemIdentifier, customItemData.toDefinition(vanillaItemIdentifier).build());
            deprecatedCustomItems.put(identifier, customItemData);
            return true;
        } catch (CustomItemDefinitionRegisterException exception) {
            return false;
        }
    }
}
