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

package org.geysermc.geyser.translator.protocol.java;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.recipe.GeyserStonecutterData;
import org.geysermc.geyser.inventory.recipe.TrimRecipe;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.cache.tags.GeyserHolderSet;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.MinecraftKey;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.HolderSet;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundUpdateRecipesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundUpdateRecipesPacket.SelectableRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Used to send all valid recipes from Java to Bedrock.
 * <p>
 * Bedrock REQUIRES a CraftingDataPacket to be sent in order to craft anything.
 */
@Translator(packet = ClientboundUpdateRecipesPacket.class)
public class JavaUpdateRecipesTranslator extends PacketTranslator<ClientboundUpdateRecipesPacket> {

    private static final List<String> NETHERITE_UPGRADES = List.of(
            "minecraft:netherite_sword",
            "minecraft:netherite_shovel",
            "minecraft:netherite_pickaxe",
            "minecraft:netherite_axe",
            "minecraft:netherite_hoe",
            "minecraft:netherite_helmet",
            "minecraft:netherite_chestplate",
            "minecraft:netherite_leggings",
            "minecraft:netherite_boots"
    );

    private static final Key SMITHING_BASE = MinecraftKey.key("smithing_base");
    private static final Key SMITHING_TEMPLATE = MinecraftKey.key("smithing_template");
    private static final Key SMITHING_ADDITION = MinecraftKey.key("smithing_addition");

    @Override
    public void translate(GeyserSession session, ClientboundUpdateRecipesPacket packet) {
        int netId = session.getLastRecipeNetId().get();
        CraftingDataPacket craftingDataPacket = new CraftingDataPacket();

        boolean oldSmithingTable;
        int[] smithingBase = packet.getItemSets().get(SMITHING_BASE);
        int[] smithingTemplate = packet.getItemSets().get(SMITHING_TEMPLATE);
        int[] smithingAddition = packet.getItemSets().get(SMITHING_ADDITION);
        if (smithingBase == null || smithingTemplate == null || smithingAddition == null) {
            // We're probably on a version before the smithing table got expanded functionality.
            oldSmithingTable = true;
            addSmithingTransformRecipes(session, craftingDataPacket.getCraftingData());
            netId = session.getLastRecipeNetId().get(); // Was updated in the above method.
        } else {
            oldSmithingTable = false;
            // BDS sends armor trim templates and materials before the CraftingDataPacket
            TrimDataPacket trimDataPacket = new TrimDataPacket();
            trimDataPacket.getPatterns().addAll(session.getRegistryCache().trimPatterns().values());
            trimDataPacket.getMaterials().addAll(session.getRegistryCache().trimMaterials().values());
            session.sendUpstreamPacket(trimDataPacket);

            // Identical smithing_trim recipe sent by BDS that uses tag-descriptors, as the client seems to ignore the
            // approach of using many default-descriptors (which we do for smithing_transform)
            craftingDataPacket.getCraftingData().add(SmithingTrimRecipeData.of(TrimRecipe.ID,
                    TrimRecipe.BASE, TrimRecipe.ADDITION, TrimRecipe.TEMPLATE, "smithing_table", netId++));
        }
        GeyserLogger.get().debug("Using old smithing table workaround? " + oldSmithingTable);
        session.setOldSmithingTable(oldSmithingTable);

        Int2ObjectMap<List<SelectableRecipe>> rawStonecutterData = new Int2ObjectOpenHashMap<>();

        List<SelectableRecipe> stonecutterRecipes = packet.getStonecutterRecipes();
        for (SelectableRecipe recipe : stonecutterRecipes) {
            // Hardcoding the heck out of this until we see different examples of how this works.
            if (!(recipe.recipe() instanceof ItemStackSlotDisplay)) {
                GeyserLogger.get().warning("Ignoring stonecutter recipe for weird output: " + recipe);
                continue;
            }

            int[] ingredients = GeyserHolderSet.fromHolderSet(JavaRegistries.ITEM, recipe.input().getValues())
                .resolveRaw(session.getTagCache());
            for (int ingredient : ingredients) {
                rawStonecutterData.computeIfAbsent(ingredient, $ -> new ArrayList<>()).add(recipe);
            }
        }

        Int2ObjectMap<GeyserStonecutterData> stonecutterRecipeMap = new Int2ObjectOpenHashMap<>();
        for (Int2ObjectMap.Entry<List<SelectableRecipe>> data : rawStonecutterData.int2ObjectEntrySet()) {
            // Implementation note: data used to have to be sorted according to the item translation key.
            // This is no longer necessary as of 1.21.2, and is instead presented in the order the server sends us.
            // (Recipes are ordered differently between Paper and vanilla)
            // See #5150.
            int buttonId = 0;
            for (SelectableRecipe recipe : data.getValue()) {
                // As of 1.16.4, all stonecutter recipes have one ingredient option
                HolderSet ingredient = recipe.input().getValues();
                int javaInput = data.getIntKey();
                ItemMapping mapping = session.getItemMappings().getMapping(javaInput);
                if (mapping.getJavaItem() == Items.AIR) {
                    // Modded ?
                    continue;
                }
                ItemDescriptorWithCount descriptor = new ItemDescriptorWithCount(new DefaultDescriptor(mapping.getBedrockDefinition(), mapping.getBedrockData()), 1);
                ItemStack javaOutput = ((ItemStackSlotDisplay) recipe.recipe()).itemStack();
                ItemData output = ItemTranslator.translateToBedrock(session, javaOutput);
                if (!output.isValid()) {
                    // Probably modded items
                    continue;
                }
                int recipeNetId = netId++;
                UUID uuid = UUID.randomUUID();
                // We need to register stonecutting recipes, so they show up on Bedrock
                // (Implementation note: recipe ID creates the order which stonecutting recipes are shown in stonecutter)
                craftingDataPacket.getCraftingData().add(ShapelessRecipeData.shapeless("stonecutter_" + javaInput + "_" + buttonId,
                    Collections.singletonList(descriptor), Collections.singletonList(output), uuid, "stonecutter", 0, recipeNetId, RecipeUnlockingRequirement.INVALID));

                // Save the recipe list for reference when crafting
                // Add the net ID as the key and the button required + output for the value
                stonecutterRecipeMap.put(recipeNetId, new GeyserStonecutterData(buttonId++, javaOutput));

                // Currently, stone cutter recipes are not locked/unlocked on Bedrock; so no need to cache their identifiers.
            }
        }

        session.sendUpstreamPacket(craftingDataPacket);
        session.setStonecutterRecipes(stonecutterRecipeMap);
        session.getLastRecipeNetId().set(netId);
    }
    
    private void addSmithingTransformRecipes(GeyserSession session, List<RecipeData> recipes) {
        ItemMapping template = session.getItemMappings().getStoredItems().upgradeTemplate();

        for (String identifier : NETHERITE_UPGRADES) {
            recipes.add(SmithingTransformRecipeData.of(identifier + "_smithing",
                    getDescriptorFromId(session, template.getBedrockIdentifier()),
                    getDescriptorFromId(session, identifier.replace("netherite", "diamond")),
                    getDescriptorFromId(session, "minecraft:netherite_ingot"),
                    ItemData.builder().definition(Objects.requireNonNull(session.getItemMappings().getDefinition(identifier))).count(1).build(),
                    "smithing_table",
                    session.getLastRecipeNetId().getAndIncrement()));
        }
    }

    private ItemDescriptorWithCount getDescriptorFromId(GeyserSession session, String bedrockId) {
        ItemDefinition bedrockDefinition = session.getItemMappings().getDefinition(bedrockId);
        if (bedrockDefinition != null) {
            return ItemDescriptorWithCount.fromItem(ItemData.builder().definition(bedrockDefinition).count(1).build());
        }
        GeyserLogger.get().debug("Unable to find item with identifier " + bedrockId);
        return ItemDescriptorWithCount.EMPTY;
    }
}
