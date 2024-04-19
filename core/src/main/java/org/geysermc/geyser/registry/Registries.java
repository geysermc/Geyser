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

package org.geysermc.geyser.registry;

import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityType;
import com.github.steveice10.mc.protocol.data.game.level.event.LevelEvent;
import com.github.steveice10.mc.protocol.data.game.level.particle.ParticleType;
import com.github.steveice10.mc.protocol.data.game.recipe.RecipeType;
import com.github.steveice10.packetlib.packet.Packet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.PotionMixData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.entity.EntityDefinition;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.event.type.GeyserDefineEntitiesEventImpl;
import org.geysermc.geyser.inventory.item.Enchantment.JavaEnchantment;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.loader.BiomeIdentifierRegistryLoader;
import org.geysermc.geyser.registry.loader.BlockEntityRegistryLoader;
import org.geysermc.geyser.registry.loader.EnchantmentRegistryLoader;
import org.geysermc.geyser.registry.loader.ParticleTypesRegistryLoader;
import org.geysermc.geyser.registry.loader.PotionMixRegistryLoader;
import org.geysermc.geyser.registry.loader.ProviderRegistryLoader;
import org.geysermc.geyser.registry.loader.RegistryLoaders;
import org.geysermc.geyser.registry.loader.SoundEventsRegistryLoader;
import org.geysermc.geyser.registry.loader.SoundRegistryLoader;
import org.geysermc.geyser.registry.loader.SoundTranslatorRegistryLoader;
import org.geysermc.geyser.registry.populator.ItemRegistryPopulator;
import org.geysermc.geyser.registry.populator.PacketRegistryPopulator;
import org.geysermc.geyser.registry.populator.RecipeRegistryPopulator;
import org.geysermc.geyser.registry.provider.ProviderSupplier;
import org.geysermc.geyser.registry.type.EnchantmentData;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.registry.type.ParticleMapping;
import org.geysermc.geyser.registry.type.SoundMapping;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.event.LevelEventTranslator;
import org.geysermc.geyser.translator.sound.SoundInteractionTranslator;
import org.geysermc.geyser.translator.sound.SoundTranslator;
import org.geysermc.geyser.util.EntityUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds all the common registries in Geyser.
 */
public final class Registries {
    /**
     * A registry holding all the providers.
     * This has to be initialized first to allow extensions to access providers during other registry events.
     */
    public static final SimpleMappedRegistry<Class<?>, ProviderSupplier> PROVIDERS = SimpleMappedRegistry.create(new IdentityHashMap<>(), ProviderRegistryLoader::new);

    /**
     * A registry holding a CompoundTag of the known entity identifiers.
     */
    public static final SimpleRegistry<NbtMap> BEDROCK_ENTITY_IDENTIFIERS = SimpleRegistry.create("bedrock/entity_identifiers.dat", RegistryLoaders.NBT);

    /**
     * A registry containing all the Bedrock packet translators.
     */
    public static final PacketTranslatorRegistry<BedrockPacket> BEDROCK_PACKET_TRANSLATORS = PacketTranslatorRegistry.create();

    /**
     * A registry holding a CompoundTag of all the known biomes.
     */
    public static final SimpleRegistry<NbtMap> BIOMES_NBT = SimpleRegistry.create("bedrock/biome_definitions.dat", RegistryLoaders.NBT);

    /**
     * A mapped registry which stores Java biome identifiers and their Bedrock biome identifier.
     */
    public static final SimpleRegistry<Object2IntMap<String>> BIOME_IDENTIFIERS = SimpleRegistry.create("mappings/biomes.json", BiomeIdentifierRegistryLoader::new);

    /**
     * A mapped registry which stores a block entity identifier to its {@link BlockEntityTranslator}.
     */
    public static final SimpleMappedRegistry<BlockEntityType, BlockEntityTranslator> BLOCK_ENTITIES = SimpleMappedRegistry.create("org.geysermc.geyser.translator.level.block.entity.BlockEntity", BlockEntityRegistryLoader::new);

    /**
     * A versioned registry which holds a {@link RecipeType} to a corresponding list of {@link RecipeData}.
     */
    public static final VersionedRegistry<Map<RecipeType, List<RecipeData>>> CRAFTING_DATA = VersionedRegistry.create(RegistryLoaders.empty(Int2ObjectOpenHashMap::new));

    /**
     * A registry holding data of all the known enchantments.
     */
    public static final SimpleMappedRegistry<JavaEnchantment, EnchantmentData> ENCHANTMENTS;

    /**
     * A map containing all entity types and their respective Geyser definitions
     */
    public static final SimpleMappedRegistry<EntityType, GeyserEntityDefinition<?>> ENTITY_DEFINITIONS = SimpleMappedRegistry.create(RegistryLoaders.empty(() -> new EnumMap<>(EntityType.class)));

    /**
     * A map containing all entity identifiers and their respective Geyser definitions
     */
    public static final SimpleMappedRegistry<String, GeyserEntityDefinition<?>> ENTITY_IDENTIFIERS = SimpleMappedRegistry.create(RegistryLoaders.empty(Object2ObjectOpenHashMap::new));

    /**
     * A registry containing all the Java packet translators.
     */
    public static final PacketTranslatorRegistry<Packet> JAVA_PACKET_TRANSLATORS = PacketTranslatorRegistry.create();

    public static final SimpleRegistry<List<Item>> JAVA_ITEMS = SimpleRegistry.create(RegistryLoaders.empty(ArrayList::new));

    public static final SimpleMappedRegistry<String, Item> JAVA_ITEM_IDENTIFIERS = SimpleMappedRegistry.create(RegistryLoaders.empty(Object2ObjectOpenHashMap::new));

    /**
     * A versioned registry which holds {@link ItemMappings} for each version. These item mappings contain
     * primarily Bedrock version-specific data.
     */
    public static final VersionedRegistry<ItemMappings> ITEMS = VersionedRegistry.create(RegistryLoaders.empty(Int2ObjectOpenHashMap::new));

    /**
     * A mapped registry holding the {@link ParticleType} to a corresponding {@link ParticleMapping}, containing various pieces of
     * data primarily for how Bedrock should handle the particle.
     */
    public static final SimpleMappedRegistry<ParticleType, ParticleMapping> PARTICLES = SimpleMappedRegistry.create("mappings/particles.json", ParticleTypesRegistryLoader::new);

    /**
     * A registry holding all the potion mixes.
     */
    public static final VersionedRegistry<Set<PotionMixData>> POTION_MIXES;

    /**
     * A versioned registry holding all the recipes, with the net ID being the key, and {@link GeyserRecipe} as the value.
     */
    public static final VersionedRegistry<Int2ObjectMap<GeyserRecipe>> RECIPES = VersionedRegistry.create(RegistryLoaders.empty(Int2ObjectOpenHashMap::new));

    /**
     * A mapped registry holding the available records, with the ID of the record being the key, and the {@link org.cloudburstmc.protocol.bedrock.data.SoundEvent}
     * as the value.
     */
    public static final IntMappedRegistry<org.cloudburstmc.protocol.bedrock.data.SoundEvent> RECORDS = IntMappedRegistry.create(RegistryLoaders.empty(Int2ObjectOpenHashMap::new));

    /**
     * A mapped registry holding {@link ResourcePack}'s with the pack uuid as keys.
     */
    public static final DeferredRegistry<Map<String, ResourcePack>> RESOURCE_PACKS = DeferredRegistry.create(GeyserImpl.getInstance().packDirectory(), SimpleMappedRegistry::create, RegistryLoaders.RESOURCE_PACKS);

    /**
     * A mapped registry holding sound identifiers to their corresponding {@link SoundMapping}.
     */
    public static final SimpleMappedRegistry<String, SoundMapping> SOUNDS = SimpleMappedRegistry.create("mappings/sounds.json", SoundRegistryLoader::new);

    /**
     * A mapped registry holding {@link LevelEvent}s to their corresponding {@link LevelEventTranslator}.
     */
    public static final SimpleMappedRegistry<LevelEvent, LevelEventTranslator> SOUND_LEVEL_EVENTS = SimpleMappedRegistry.create("mappings/effects.json", SoundEventsRegistryLoader::new);

    /**
     * A mapped registry holding {@link SoundTranslator}s to their corresponding {@link SoundInteractionTranslator}.
     */
    public static final SimpleMappedRegistry<SoundTranslator, SoundInteractionTranslator<?>> SOUND_TRANSLATORS = SimpleMappedRegistry.create("org.geysermc.geyser.translator.sound.SoundTranslator", SoundTranslatorRegistryLoader::new);

    public static void init() {
        // no-op
    }

    static {
        PacketRegistryPopulator.populate();
        ItemRegistryPopulator.populate();
        RecipeRegistryPopulator.populate();

        // Create registries that require other registries to load first
        POTION_MIXES = VersionedRegistry.create(PotionMixRegistryLoader::new);
        ENCHANTMENTS = SimpleMappedRegistry.create("mappings/enchantments.json", EnchantmentRegistryLoader::new);

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

    public static void callRegistryEvents() {
        // Call registry events
        List<EntityDefinition> definitions = ENTITY_IDENTIFIERS.get().values().stream()
                .map(def -> (EntityDefinition) def)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        GeyserDefineEntitiesEvent defineEntitiesEvent = new GeyserDefineEntitiesEventImpl(definitions) {
            @Override
            public boolean register(@NonNull EntityDefinition entityDefinition) {
                GeyserEntityDefinition<?> geyserDefinition = (GeyserEntityDefinition<?>) entityDefinition;
                if (!geyserDefinition.custom()) {
                    return false;
                }

                EntityUtils.registerEntity(geyserDefinition.identifier(), geyserDefinition);
                return true;
            }
        };

        GeyserImpl.getInstance().eventBus().fire(defineEntitiesEvent);
    }
}
