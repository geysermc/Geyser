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

package org.geysermc.geyser.event.type;

import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.exception.ResourcePackException;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.ResourcePackHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
public class GeyserDefineResourcePacksEventImpl extends GeyserDefineResourcePacksEvent {
    private final Map<UUID, ResourcePackHolder> packs;

    public GeyserDefineResourcePacksEventImpl(Map<UUID, ResourcePackHolder> packMap) {
        this.packs = packMap;
    }

    @Override
    public @NonNull List<ResourcePack> resourcePacks() {
        return packs.values().stream().map(ResourcePackHolder::resourcePack).toList();
    }

    @Override
    public void register(@NonNull ResourcePack resourcePack, @Nullable ResourcePackOption<?>... options) {
        Objects.requireNonNull(resourcePack, "resource pack must not be null!");
        if (!(resourcePack instanceof GeyserResourcePack pack)) {
            throw new ResourcePackException(ResourcePackException.Cause.UNKNOWN_IMPLEMENTATION);
        }

        UUID uuid = resourcePack.uuid();
        if (packs.containsKey(uuid)) {
            throw new ResourcePackException(ResourcePackException.Cause.DUPLICATE);
        }

        ResourcePackHolder holder = ResourcePackHolder.of(pack);
        attemptRegisterOptions(holder, options);
        packs.put(uuid, holder);
    }

    @Override
    public void registerOptions(@NonNull UUID uuid, @NonNull ResourcePackOption<?>... options) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(options);

        ResourcePackHolder holder = packs.get(uuid);
        if (holder == null) {
            throw new ResourcePackException(ResourcePackException.Cause.PACK_NOT_FOUND);
        }

        attemptRegisterOptions(holder, options);
    }

    @Override
    public Collection<ResourcePackOption<?>> options(@NonNull UUID uuid) {
        Objects.requireNonNull(uuid);
        ResourcePackHolder packHolder = packs.get(uuid);
        if (packHolder == null) {
            throw new ResourcePackException(ResourcePackException.Cause.PACK_NOT_FOUND);
        }

        return packHolder.optionHolder().immutableValues();
    }

    @Override
    public @Nullable ResourcePackOption<?> option(@NonNull UUID uuid, ResourcePackOption.@NonNull Type type) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(type);

        ResourcePackHolder packHolder = packs.get(uuid);
        if (packHolder == null) {
            throw new ResourcePackException(ResourcePackException.Cause.PACK_NOT_FOUND);
        }

        return packHolder.optionHolder().get(type);
    }

    @Override
    public void unregister(@NonNull UUID uuid) {
        packs.remove(uuid);
    }

    private void attemptRegisterOptions(@NonNull ResourcePackHolder holder, @Nullable ResourcePackOption<?>... options) {
        if (options == null) {
            return;
        }

        holder.optionHolder().validateAndAdd(holder.pack(), options);
    }
}
