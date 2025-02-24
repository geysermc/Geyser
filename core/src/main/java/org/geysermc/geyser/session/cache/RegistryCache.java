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

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.TrimMaterial;
import org.cloudburstmc.protocol.bedrock.data.TrimPattern;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.type.living.animal.tameable.WolfEntity;
import org.geysermc.geyser.inventory.item.BannerPattern;
import org.geysermc.geyser.inventory.item.GeyserInstrument;
import org.geysermc.geyser.inventory.recipe.TrimRecipe;
import org.geysermc.geyser.item.enchantment.Enchantment;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.JukeboxSong;
import org.geysermc.geyser.level.PaintingType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.cache.registry.JavaRegistry;
import org.geysermc.geyser.session.cache.registry.JavaRegistryKey;
import org.geysermc.geyser.session.cache.registry.RegistryEntryContext;
import org.geysermc.geyser.session.cache.registry.RegistryEntryData;
import org.geysermc.geyser.session.cache.registry.SimpleJavaRegistry;
import org.geysermc.geyser.text.ChatDecoration;
import org.geysermc.geyser.translator.level.BiomeTranslator;
import org.geysermc.geyser.util.MinecraftKey;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatType;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Stores any information sent via Java registries. May not contain all data in a given registry - we'll strip what's
 * unneeded.
 *
 * Crafted as of 1.20.5 for easy "add new registry" functionality in the future.
 */
@Accessors(fluent = true)
@Getter
public final class RegistryCache {
    private static final Map<Key, Map<Key, NbtMap>> DEFAULTS;
    private static final Map<Key, BiConsumer<RegistryCache, List<RegistryEntry>>> REGISTRIES = new HashMap<>();

    static {
        register("chat_type", cache -> cache.chatTypes, ChatDecoration::readChatType);
        register("dimension_type", cache -> cache.dimensions, JavaDimension::read);
        register(JavaRegistries.ENCHANTMENT, cache -> cache.enchantments, Enchantment::read);
        register("instrument", cache -> cache.instruments, GeyserInstrument::read);
        register("jukebox_song", cache -> cache.jukeboxSongs, JukeboxSong::read);
        register("painting_variant", cache -> cache.paintings, context -> PaintingType.getByName(context.id()));
        register("trim_material", cache -> cache.trimMaterials, TrimRecipe::readTrimMaterial);
        register("trim_pattern", cache -> cache.trimPatterns, TrimRecipe::readTrimPattern);
        register("worldgen/biome", (cache, array) -> cache.biomeTranslations = array, BiomeTranslator::loadServerBiome);
        register("banner_pattern", cache -> cache.bannerPatterns, context -> BannerPattern.getByJavaIdentifier(context.id()));
        register("wolf_variant", cache -> cache.wolfVariants, context -> WolfEntity.BuiltInWolfVariant.getByJavaIdentifier(context.id().asString()));

        // Load from MCProtocolLib's classloader
        NbtMap tag = MinecraftProtocol.loadNetworkCodec();
        Map<Key, Map<Key, NbtMap>> defaults = new HashMap<>();
        // Don't create a keySet - no need to create the cached object in HashMap if we don't use it again
        REGISTRIES.forEach((key, $) -> {
            List<NbtMap> rawValues = tag.getCompound(key.asString()).getList("value", NbtType.COMPOUND);
            Map<Key, NbtMap> values = new HashMap<>();
            for (NbtMap value : rawValues) {
                Key name = MinecraftKey.key(value.getString("name"));
                values.put(name, value.getCompound("element"));
            }
            // Can make these maps immutable and as efficient as possible after initialization
            defaults.put(key, Map.copyOf(values));
        });

        DEFAULTS = Map.copyOf(defaults);
    }

    @Getter(AccessLevel.NONE)
    private final GeyserSession session;

    /**
     * Java -> Bedrock biome network IDs.
     */
    private int[] biomeTranslations;
    private final JavaRegistry<ChatType> chatTypes = new SimpleJavaRegistry<>();
    /**
     * All dimensions that the client could possibly connect to.
     */
    private final JavaRegistry<JavaDimension> dimensions = new SimpleJavaRegistry<>();
    private final JavaRegistry<Enchantment> enchantments = new SimpleJavaRegistry<>();
    private final JavaRegistry<JukeboxSong> jukeboxSongs = new SimpleJavaRegistry<>();
    private final JavaRegistry<PaintingType> paintings = new SimpleJavaRegistry<>();
    private final JavaRegistry<TrimMaterial> trimMaterials = new SimpleJavaRegistry<>();
    private final JavaRegistry<TrimPattern> trimPatterns = new SimpleJavaRegistry<>();

    private final JavaRegistry<BannerPattern> bannerPatterns = new SimpleJavaRegistry<>();
    private final JavaRegistry<WolfEntity.BuiltInWolfVariant> wolfVariants = new SimpleJavaRegistry<>();
    private final JavaRegistry<GeyserInstrument> instruments = new SimpleJavaRegistry<>();

    public RegistryCache(GeyserSession session) {
        this.session = session;
    }

    /**
     * Loads a registry in, if we are tracking it.
     */
    public void load(ClientboundRegistryDataPacket packet) {
        var reader = REGISTRIES.get(packet.getRegistry());
        if (reader != null) {
            reader.accept(this, packet.getEntries());
        } else {
            GeyserImpl.getInstance().getLogger().debug("Ignoring registry of type " + packet.getRegistry());
        }
    }

    /**
     * @param registry the Java registry resource location, without the "minecraft:" prefix.
     * @param localCacheFunction which local field in RegistryCache are we caching entries for this registry?
     * @param reader converts the RegistryEntry NBT into a class file
     * @param <T> the class that represents these entries.
     */
    private static <T> void register(String registry, Function<RegistryCache, JavaRegistry<T>> localCacheFunction, Function<RegistryEntryContext, T> reader) {
        register(MinecraftKey.key(registry), localCacheFunction, reader);
    }

    /**
     * @param registry the Java registry resource location.
     * @param localCacheFunction which local field in RegistryCache are we caching entries for this registry?
     * @param reader converts the RegistryEntry NBT into a class file
     * @param <T> the class that represents these entries.
     */
    private static <T> void register(JavaRegistryKey<?> registry, Function<RegistryCache, JavaRegistry<T>> localCacheFunction, Function<RegistryEntryContext, T> reader) {
        register(registry.registryKey(), localCacheFunction, reader);
    }

    /**
     * @param registry the Java registry resource location.
     * @param localCacheFunction which local field in RegistryCache are we caching entries for this registry?
     * @param reader converts the RegistryEntry NBT into a class file
     * @param <T> the class that represents these entries.
     */
    private static <T> void register(Key registry, Function<RegistryCache, JavaRegistry<T>> localCacheFunction, Function<RegistryEntryContext, T> reader) {
        REGISTRIES.put(registry, (registryCache, entries) -> {
            Map<Key, NbtMap> localRegistry = null;
            JavaRegistry<T> localCache = localCacheFunction.apply(registryCache);
            // Clear each local cache every time a new registry entry is given to us
            // (e.g. proxy server switches)

            // Store each of the entries resource location IDs and their respective network ID,
            // used for the key mapper that's currently only used by the Enchantment class
            Object2IntMap<Key> entryIdMap = new Object2IntOpenHashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                entryIdMap.put(entries.get(i).getId(), i);
            }

            List<RegistryEntryData<T>> builder = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                RegistryEntry entry = entries.get(i);
                // If the data is null, that's the server telling us we need to use our default values.
                if (entry.getData() == null) {
                    if (localRegistry == null) { // Lazy initialize
                        localRegistry = DEFAULTS.get(registry);
                    }
                    entry = new RegistryEntry(entry.getId(), localRegistry.get(entry.getId()));
                }

                RegistryEntryContext context = new RegistryEntryContext(entry, entryIdMap, registryCache.session);
                // This is what Geyser wants to keep as a value for this registry.
                T cacheEntry = reader.apply(context);
                builder.add(i, new RegistryEntryData<>(entry.getId(), cacheEntry));
            }
            localCache.reset(builder);
        });
    }

    /**
     * @param localCacheFunction the int array to set the final values to.
     */
    private static void register(String registry, BiConsumer<RegistryCache, int[]> localCacheFunction, ToIntFunction<RegistryEntry> reader) {
        REGISTRIES.put(MinecraftKey.key(registry), (registryCache, entries) -> {
            Int2IntMap temp = new Int2IntOpenHashMap();
            int greatestId = 0;
            for (int i = 0; i < entries.size(); i++) {
                RegistryEntry entry = entries.get(i);
                // This is what Geyser wants to keep as a value for this registry.
                int cacheEntry = reader.applyAsInt(entry);
                temp.put(i, cacheEntry);
                if (i > greatestId) {
                    // Maximum registry ID, so far. Make sure the final array is at least this large.
                    greatestId = i;
                }
            }

            int[] array = new int[greatestId + 1];
            for (Int2IntMap.Entry entry : temp.int2IntEntrySet()) {
                array[entry.getIntKey()] = entry.getIntValue();
            }
            localCacheFunction.accept(registryCache, array);
        });
    }

    public static void init() {
        // no-op
    }
}
