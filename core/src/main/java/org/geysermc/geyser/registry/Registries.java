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

package org.geysermc.geyser.registry;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.PotionMixData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.pack.ResourcePackHolder;
import org.geysermc.geyser.registry.loader.BiomeIdentifierRegistryLoader;
import org.geysermc.geyser.registry.loader.BlockEntityRegistryLoader;
import org.geysermc.geyser.registry.loader.ParticleTypesRegistryLoader;
import org.geysermc.geyser.registry.loader.PotionMixRegistryLoader;
import org.geysermc.geyser.registry.loader.ProviderRegistryLoader;
import org.geysermc.geyser.registry.loader.RegistryLoaders;
import org.geysermc.geyser.registry.loader.SoundEventsRegistryLoader;
import org.geysermc.geyser.registry.loader.SoundRegistryLoader;
import org.geysermc.geyser.registry.loader.SoundTranslatorRegistryLoader;
import org.geysermc.geyser.registry.populator.DataComponentRegistryPopulator;
import org.geysermc.geyser.registry.populator.ItemRegistryPopulator;
import org.geysermc.geyser.registry.populator.PacketRegistryPopulator;
import org.geysermc.geyser.registry.populator.TagRegistryPopulator;
import org.geysermc.geyser.registry.provider.ProviderSupplier;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.registry.type.ParticleMapping;
import org.geysermc.geyser.registry.type.SoundMapping;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.event.LevelEventTranslator;
import org.geysermc.geyser.translator.sound.SoundInteractionTranslator;
import org.geysermc.geyser.translator.sound.SoundTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.event.LevelEvent;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holds all the common registries in Geyser.
 */
public final class Registries {
    private static boolean loaded = false;

    /**
     * A registry holding all the providers.
     * This has to be initialized first to allow extensions to access providers during other registry events.
     */
    public static final SimpleMappedRegistry<Class<?>, ProviderSupplier> PROVIDERS = SimpleMappedRegistry.create(new IdentityHashMap<>(), ProviderRegistryLoader::new);

    /**
     * A registry holding a NbtMap of the known entity identifiers.
     */
    public static final SimpleDeferredRegistry<NbtMap> BEDROCK_ENTITY_IDENTIFIERS = SimpleDeferredRegistry.create("bedrock/entity_identifiers.dat", RegistryLoaders.NBT);

    /**
     * A registry containing all the Bedrock packet translators.
     */
    public static final PacketTranslatorRegistry<BedrockPacket> BEDROCK_PACKET_TRANSLATORS = PacketTranslatorRegistry.create();

    /**
     * A registry holding a NbtMap of all the known biomes.
     */
    public static final SimpleDeferredRegistry<NbtMap> BIOMES_NBT = SimpleDeferredRegistry.create("bedrock/biome_definitions.dat", RegistryLoaders.NBT);

    /**
     * A mapped registry which stores Java biome identifiers and their Bedrock biome identifier.
     */
    public static final SimpleDeferredRegistry<Object2IntMap<String>> BIOME_IDENTIFIERS = SimpleDeferredRegistry.create("mappings/biomes.json", BiomeIdentifierRegistryLoader::new);

    /**
     * A mapped registry which stores a block entity identifier to its {@link BlockEntityTranslator}.
     */
    public static final SimpleMappedDeferredRegistry<BlockEntityType, BlockEntityTranslator> BLOCK_ENTITIES = SimpleMappedDeferredRegistry.create("org.geysermc.geyser.translator.level.block.entity.BlockEntity", BlockEntityRegistryLoader::new);

    /**
     * A map containing all entity types and their respective Geyser definitions
     */
    public static final SimpleMappedRegistry<EntityType, EntityDefinition<?>> ENTITY_DEFINITIONS = SimpleMappedRegistry.create(RegistryLoaders.empty(() -> new EnumMap<>(EntityType.class)));

    /**
     * A registry holding a list of all the known entity properties to be sent to the client after start game.
     */
    public static final SimpleRegistry<Set<NbtMap>> BEDROCK_ENTITY_PROPERTIES = SimpleRegistry.create(RegistryLoaders.empty(HashSet::new));

    /**
     * A map containing all Java entity identifiers and their respective Geyser definitions
     */
    public static final SimpleMappedRegistry<String, EntityDefinition<?>> JAVA_ENTITY_IDENTIFIERS = SimpleMappedRegistry.create(RegistryLoaders.empty(Object2ObjectOpenHashMap::new));

    /**
     * A registry containing all the Java packet translators.
     */
    public static final PacketTranslatorRegistry<Packet> JAVA_PACKET_TRANSLATORS = PacketTranslatorRegistry.create();

    /**
     * A registry containing all Java items ordered by their network ID.
     */
    public static final ListRegistry<Item> JAVA_ITEMS = ListRegistry.create(RegistryLoaders.empty(ArrayList::new));

    /**
     * A registry containing item identifiers.
     */
    public static final SimpleMappedRegistry<String, Item> JAVA_ITEM_IDENTIFIERS = SimpleMappedRegistry.create(RegistryLoaders.empty(Object2ObjectOpenHashMap::new));

    public static final ListRegistry<DataComponents> DEFAULT_DATA_COMPONENTS = ListRegistry.create(RegistryLoaders.empty(ArrayList::new));

    /**
     * A versioned registry which holds {@link ItemMappings} for each version. These item mappings contain
     * primarily Bedrock version-specific data.
     */
    public static final VersionedRegistry<ItemMappings> ITEMS = VersionedRegistry.create(RegistryLoaders.empty(Int2ObjectOpenHashMap::new));

    /**
     * A mapped registry holding the {@link ParticleType} to a corresponding {@link ParticleMapping}, containing various pieces of
     * data primarily for how Bedrock should handle the particle.
     */
    public static final SimpleMappedDeferredRegistry<ParticleType, ParticleMapping> PARTICLES = SimpleMappedDeferredRegistry.create("mappings/particles.json", ParticleTypesRegistryLoader::new);

    /**
     * A registry holding all the potion mixes.
     */
    public static final VersionedDeferredRegistry<Set<PotionMixData>> POTION_MIXES = VersionedDeferredRegistry.create(VersionedRegistry::create, PotionMixRegistryLoader::new);

    /**
     * A versioned registry holding all the recipes, with the net ID being the key, and {@link GeyserRecipe} as the value.
     */
    //public static final SimpleMappedDeferredRegistry<RecipeType, List<GeyserRecipe>> RECIPES = SimpleMappedDeferredRegistry.create("mappings/recipes.nbt", RecipeRegistryLoader::new);

    /**
     * A mapped registry holding {@link ResourcePackHolder}'s with the pack uuid as keys.
     */
    public static final SimpleMappedDeferredRegistry<UUID, ResourcePackHolder> RESOURCE_PACKS = SimpleMappedDeferredRegistry.create(GeyserImpl.getInstance().packDirectory(), RegistryLoaders.RESOURCE_PACKS);

    /**
     * A versioned registry holding most Bedrock tags, with the Java item list (sorted) being the key, and the tag name as the value.
     */
    public static final VersionedRegistry<Object2ObjectMap<int[], String>> TAGS = VersionedRegistry.create(RegistryLoaders.empty(Int2ObjectOpenHashMap::new));

    /**
     * A mapped registry holding sound identifiers to their corresponding {@link SoundMapping}.
     */
    public static final SimpleMappedDeferredRegistry<String, SoundMapping> SOUNDS = SimpleMappedDeferredRegistry.create("mappings/sounds.json", SoundRegistryLoader::new);

    /**
     * A mapped registry holding {@link LevelEvent}s to their corresponding {@link LevelEventTranslator}.
     */
    public static final SimpleMappedDeferredRegistry<LevelEvent, LevelEventTranslator> SOUND_LEVEL_EVENTS = SimpleMappedDeferredRegistry.create("mappings/effects.json", SoundEventsRegistryLoader::new);

    /**
     * A mapped registry holding {@link SoundTranslator}s to their corresponding {@link SoundInteractionTranslator}.
     */
    public static final SimpleMappedDeferredRegistry<SoundTranslator, SoundInteractionTranslator<?>> SOUND_TRANSLATORS = SimpleMappedDeferredRegistry.create("org.geysermc.geyser.translator.sound.SoundTranslator", SoundTranslatorRegistryLoader::new);

    public static void load() {
        if (loaded) return;
        loaded = true;

        // the following registries are registries that are more complicated than initializing as an empty collection.
        // They generally have in common that they either depend on loading a resource file directly or indirectly
        // (by using the Items or Blocks class, which loads all the blocks)

        BEDROCK_ENTITY_IDENTIFIERS.load();
        BIOMES_NBT.load();
        BIOME_IDENTIFIERS.load();
        BLOCK_ENTITIES.load();
        PARTICLES.load();
        // load potion mixes later
        //RECIPES.load();
        RESOURCE_PACKS.load();
        SOUNDS.load();
        SOUND_LEVEL_EVENTS.load();
        SOUND_TRANSLATORS.load();
    }

    public static void populate() {
        PacketRegistryPopulator.populate();
        DataComponentRegistryPopulator.populate();
        ItemRegistryPopulator.populate();
        TagRegistryPopulator.populate();

        // potion mixes depend on other registries
        POTION_MIXES.load();

        // Remove unneeded client generation data from NbtMapBuilder
        NbtMapBuilder biomesNbt = NbtMap.builder();
        for (Map.Entry<String, Object> entry : BIOMES_NBT.get().entrySet()) {
            String key = entry.getKey();
            NbtMapBuilder value = ((NbtMap) entry.getValue()).toBuilder();
            value.remove("minecraft:consolidated_features");
            value.remove("minecraft:multinoise_generation_rules");
            value.remove("minecraft:surface_material_adjustments");
            value.remove( "minecraft:surface_parameters");
            biomesNbt.put(key, value.build());
        }
        BIOMES_NBT.set(biomesNbt.build());
    }
}
