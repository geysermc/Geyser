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

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.api.pack.exception.ResourcePackException;
import org.geysermc.geyser.api.pack.option.PriorityOption;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.ResourcePackHolder;
import org.geysermc.geyser.pack.option.OptionHolder;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SessionLoadResourcePacksEventImpl extends SessionLoadResourcePacksEvent {

    /**
     * The packs for this Session. A {@link ResourcePackHolder} may contain resource pack options registered
     * during the {@link org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent}.
     */
    @Getter
    private final Map<UUID, ResourcePackHolder> packs;

    /**
     * The additional, per-session options for the resource packs of this session.
     * These options are prioritized over the "default" options registered
     * in the {@link org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent}
     */
    private final Map<UUID, OptionHolder> sessionPackOptionOverrides;

    public SessionLoadResourcePacksEventImpl(GeyserSession session) {
        super(session);
        this.packs = new Object2ObjectLinkedOpenHashMap<>(Registries.RESOURCE_PACKS.get());
        this.sessionPackOptionOverrides = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public @NonNull List<ResourcePack> resourcePacks() {
        return packs.values().stream().map(ResourcePackHolder::resourcePack).toList();
    }

    @Override
    public boolean register(@NonNull ResourcePack resourcePack) {
        try {
            register(resourcePack, PriorityOption.NORMAL);
        } catch (ResourcePackException e) {
            GeyserImpl.getInstance().getLogger().error("An exception occurred while registering resource pack: " + e.getMessage(), e);
            return false;
        }
        return true;
    }

    @Override
    public void register(@NonNull ResourcePack resourcePack, @Nullable ResourcePackOption<?>... options) {
        Objects.requireNonNull(resourcePack);
        if (!(resourcePack instanceof GeyserResourcePack pack)) {
            throw new ResourcePackException(ResourcePackException.Cause.UNKNOWN_IMPLEMENTATION);
        }

        UUID uuid = resourcePack.uuid();
        if (packs.containsKey(uuid)) {
            throw new ResourcePackException(ResourcePackException.Cause.DUPLICATE);
        }

        attemptRegisterOptions(pack, options);
        packs.put(uuid, ResourcePackHolder.of(pack));
    }

    @Override
    public void registerOptions(@NonNull UUID uuid, @NonNull ResourcePackOption<?>... options) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(options, "options cannot be null");
        ResourcePackHolder holder = packs.get(uuid);
        if (holder == null) {
            throw new ResourcePackException(ResourcePackException.Cause.PACK_NOT_FOUND);
        }

        attemptRegisterOptions(holder.pack(), options);
    }

    @Override
    public Collection<ResourcePackOption<?>> options(@NonNull UUID uuid) {
        Objects.requireNonNull(uuid);
        ResourcePackHolder packHolder = packs.get(uuid);
        if (packHolder == null) {
            throw new ResourcePackException(ResourcePackException.Cause.PACK_NOT_FOUND);
        }

        OptionHolder optionHolder = sessionPackOptionOverrides.get(uuid);
        if (optionHolder == null) {
            // No need to create a new session option holder
            return packHolder.optionHolder().immutableValues();
        }

        return optionHolder.immutableValues(packHolder.optionHolder());
    }

    @Override
    public @Nullable ResourcePackOption<?> option(@NonNull UUID uuid, ResourcePackOption.@NonNull Type type) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(type);

        ResourcePackHolder packHolder = packs.get(uuid);
        if (packHolder == null) {
            throw new ResourcePackException(ResourcePackException.Cause.PACK_NOT_FOUND);
        }

        @Nullable OptionHolder additionalOptions = sessionPackOptionOverrides.get(uuid);
        OptionHolder defaultHolder = packHolder.optionHolder();
        Objects.requireNonNull(defaultHolder); // should never be null

        return OptionHolder.optionByType(type, additionalOptions, defaultHolder);
    }

    @Override
    public boolean unregister(@NonNull UUID uuid) {
        sessionPackOptionOverrides.remove(uuid);
        return packs.remove(uuid) != null;
    }

    private void attemptRegisterOptions(@NonNull GeyserResourcePack pack, @Nullable ResourcePackOption<?>... options) {
        if (options == null) {
            return;
        }

        OptionHolder holder = this.sessionPackOptionOverrides.computeIfAbsent(pack.uuid(), $ -> new OptionHolder());
        holder.validateAndAdd(pack, options);
    }

    // Methods used internally for e.g. ordered packs, or resource pack entries

    public List<ResourcePackStackPacket.Entry> orderedPacks() {
        return packs.values().stream()
            // Map each ResourcePack to a pair of (GeyserResourcePack, Priority)
            .map(holder -> new AbstractMap.SimpleEntry<>(holder.pack(), priority(holder.pack())))
            // Sort by priority in ascending order
            .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
            // Map the sorted entries to ResourcePackStackPacket.Entry
            .map(entry -> {
                ResourcePackManifest.Header header = entry.getKey().manifest().header();
                return new ResourcePackStackPacket.Entry(
                    header.uuid().toString(),
                    header.version().toString(),
                    subpackName(entry.getKey())
                );
            })
            .toList();
    }

    public List<ResourcePacksInfoPacket.Entry> infoPacketEntries() {
        List<ResourcePacksInfoPacket.Entry> entries = new ArrayList<>();

        for (ResourcePackHolder holder : packs.values()) {
            GeyserResourcePack pack = holder.pack();
            ResourcePackManifest.Header header = pack.manifest().header();
            entries.add(new ResourcePacksInfoPacket.Entry(
                header.uuid(), header.version().toString(), pack.codec().size(), pack.contentKey(),
                subpackName(pack), header.uuid().toString(), false, false, false, subpackName(pack))
            );
        }

        return entries;
    }

    // Helper methods to get the options for a ResourcePack

    public <T> T value(UUID uuid, ResourcePackOption.Type type, T defaultValue) {
        OptionHolder holder = sessionPackOptionOverrides.get(uuid);
        OptionHolder defaultHolder = packs.get(uuid).optionHolder();
        Objects.requireNonNull(defaultHolder); // should never be null

        return OptionHolder.valueOrFallback(type, holder, defaultHolder, defaultValue);
    }

    private double priority(GeyserResourcePack pack) {
        return value(pack.uuid(), ResourcePackOption.Type.PRIORITY, PriorityOption.NORMAL.value());
    }

    private String subpackName(GeyserResourcePack pack) {
        return value(pack.uuid(), ResourcePackOption.Type.SUBPACK, "");
    }
}
