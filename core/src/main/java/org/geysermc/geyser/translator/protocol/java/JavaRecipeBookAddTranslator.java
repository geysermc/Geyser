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

package org.geysermc.geyser.translator.protocol.java;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.key.Key;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserShapedRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserShapelessRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserSmithingRecipe;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.BedrockRequiresTagItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.cache.tags.Tag;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.RecipeDisplayEntry;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapedCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.ShapelessCraftingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.SmithingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.CompositeSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.EmptySlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SmithingTrimDemoSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.TagSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.WithRemainderSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Translator(packet = ClientboundRecipeBookAddPacket.class)
public class JavaRecipeBookAddTranslator extends PacketTranslator<ClientboundRecipeBookAddPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundRecipeBookAddPacket packet) {
        int netId = session.getLastRecipeNetId().get();
        Int2ObjectMap<List<String>> javaToBedrockRecipeIds = session.getJavaToBedrockRecipeIds();
        Int2ObjectMap<GeyserRecipe> geyserRecipes = session.getCraftingRecipes();
        CraftingDataPacket craftingDataPacket = new CraftingDataPacket();

        UnlockedRecipesPacket recipesPacket = new UnlockedRecipesPacket();
        recipesPacket.setAction(packet.isReplace() ? UnlockedRecipesPacket.ActionType.INITIALLY_UNLOCKED : UnlockedRecipesPacket.ActionType.NEWLY_UNLOCKED);

        for (ClientboundRecipeBookAddPacket.Entry entry : packet.getEntries()) {
            RecipeDisplayEntry contents = entry.contents();
            RecipeDisplay display = contents.display();

            switch (display.getType()) {
                case CRAFTING_SHAPED -> {
                    ShapedCraftingRecipeDisplay shapedRecipe = (ShapedCraftingRecipeDisplay) display;
                    var bedrockRecipes = combinations(session, display, shapedRecipe.ingredients());
                    if (bedrockRecipes == null) {
                        continue;
                    }
                    List<String> bedrockRecipeIds = new ArrayList<>();
                    ItemData output = bedrockRecipes.right();
                    List<List<ItemDescriptorWithCount>> left = bedrockRecipes.left();
                    GeyserRecipe geyserRecipe = new GeyserShapedRecipe(shapedRecipe);
                    for (int i = 0; i < left.size(); i++) {
                        List<ItemDescriptorWithCount> inputs = left.get(i);
                        String recipeId = contents.id() + "_" + i;
                        int recipeNetworkId = netId++;
                        craftingDataPacket.getCraftingData().add(ShapedRecipeData.shaped(recipeId,
                            shapedRecipe.width(), shapedRecipe.height(), inputs,
                            Collections.singletonList(output), UUID.randomUUID(), "crafting_table", 0, recipeNetworkId, false, RecipeUnlockingRequirement.INVALID));
                        recipesPacket.getUnlockedRecipes().add(recipeId);
                        bedrockRecipeIds.add(recipeId);
                        geyserRecipes.put(recipeNetworkId, geyserRecipe);
                    }
                    javaToBedrockRecipeIds.put(contents.id(), List.copyOf(bedrockRecipeIds));
                }
                case CRAFTING_SHAPELESS -> {
                    ShapelessCraftingRecipeDisplay shapelessRecipe = (ShapelessCraftingRecipeDisplay) display;
                    var bedrockRecipes = combinations(session, display, shapelessRecipe.ingredients());
                    if (bedrockRecipes == null) {
                        continue;
                    }
                    List<String> bedrockRecipeIds = new ArrayList<>();
                    ItemData output = bedrockRecipes.right();
                    List<List<ItemDescriptorWithCount>> left = bedrockRecipes.left();
                    GeyserRecipe geyserRecipe = new GeyserShapelessRecipe(shapelessRecipe);
                    for (int i = 0; i < left.size(); i++) {
                        List<ItemDescriptorWithCount> inputs = left.get(i);
                        String recipeId = contents.id() + "_" + i;
                        int recipeNetworkId = netId++;
                        craftingDataPacket.getCraftingData().add(ShapelessRecipeData.shapeless(recipeId,
                            inputs, Collections.singletonList(output), UUID.randomUUID(), "crafting_table", 0, recipeNetworkId, RecipeUnlockingRequirement.INVALID));
                        recipesPacket.getUnlockedRecipes().add(recipeId);
                        bedrockRecipeIds.add(recipeId);
                        geyserRecipes.put(recipeNetworkId, geyserRecipe);
                    }
                    javaToBedrockRecipeIds.put(contents.id(), List.copyOf(bedrockRecipeIds));
                }
                case SMITHING -> {
                    if (display.result() instanceof SmithingTrimDemoSlotDisplay) {
                        // Skip these - Bedrock already knows about them from the TrimDataPacket
                        continue;
                    }
                    SmithingRecipeDisplay smithingRecipe = (SmithingRecipeDisplay) display;
                    Pair<Item, ItemData> output = translateToOutput(session, smithingRecipe.result());
                    if (output == null) {
                        continue;
                    }

                    List<ItemDescriptorWithCount> bases = translateToInput(session, smithingRecipe.base());
                    List<ItemDescriptorWithCount> templates = translateToInput(session, smithingRecipe.template());
                    List<ItemDescriptorWithCount> additions = translateToInput(session, smithingRecipe.addition());

                    if (bases == null || templates == null || additions == null) {
                        continue;
                    }

                    int i = 0;
                    List<String> bedrockRecipeIds = new ArrayList<>();
                    for (ItemDescriptorWithCount template : templates) {
                        for (ItemDescriptorWithCount base : bases) {
                            for (ItemDescriptorWithCount addition : additions) {
                                String id = contents.id() + "_" + i++;
                                // Note: vanilla inputs use aux value of Short.MAX_VALUE
                                craftingDataPacket.getCraftingData().add(SmithingTransformRecipeData.of(id,
                                        template, base, addition, output.right(), "smithing_table", netId++));

                                recipesPacket.getUnlockedRecipes().add(id);
                                bedrockRecipeIds.add(id);
                            }
                        }
                    }
                    javaToBedrockRecipeIds.put(contents.id(), bedrockRecipeIds);
                    session.getSmithingRecipes().add(new GeyserSmithingRecipe(smithingRecipe));
                }
            }
        }

        if (!recipesPacket.getUnlockedRecipes().isEmpty()) {
            // Sending an empty list here will crash the client as of 1.20.60
            // This was definitely in the codebase the entire time and did not
            // accidentally get refactored out during Java 1.21.3. :)
            session.sendUpstreamPacket(craftingDataPacket);
            session.sendUpstreamPacket(recipesPacket);
        }
        session.getLastRecipeNetId().set(netId);

        // Multi-version can mean different Bedrock item IDs
        TAG_TO_ITEM_DESCRIPTOR_CACHE.remove();
    }

    // Arrays are usually an issue in maps, but because it's referencing the tag array that is unchanged, it actually works out for us.
    private static final ThreadLocal<Map<int[], List<ItemDescriptorWithCount>>> TAG_TO_ITEM_DESCRIPTOR_CACHE = ThreadLocal.withInitial(Object2ObjectOpenHashMap::new);

    private List<ItemDescriptorWithCount> translateToInput(GeyserSession session, SlotDisplay slotDisplay) {
        if (slotDisplay instanceof EmptySlotDisplay) {
            return Collections.singletonList(ItemDescriptorWithCount.EMPTY);
        }
        if (slotDisplay instanceof CompositeSlotDisplay composite) {
            if (composite.contents().size() == 1) {
                return translateToInput(session, composite.contents().get(0));
            }

            // Try and see if the contents match a tag.
            // ViaVersion maps pre-1.21.2 ingredient lists to CompositeSlotDisplays.
            int[] items = new int[composite.contents().size()];
            List<SlotDisplay> contents = composite.contents();
            for (int i = 0; i < contents.size(); i++) {
                SlotDisplay subDisplay = contents.get(i);
                int id;
                if (subDisplay instanceof ItemSlotDisplay item) {
                    id = item.item();
                } else if (!(subDisplay instanceof ItemStackSlotDisplay itemStackSlotDisplay)) {
                    id = -1;
                } else if (itemStackSlotDisplay.itemStack().getAmount() == 1
                    && itemStackSlotDisplay.itemStack().getDataComponentsPatch() == null) {
                    id = itemStackSlotDisplay.itemStack().getId();
                } else {
                    id = -1;
                }
                if (id == -1) {
                    // We couldn't guarantee a "normal" item from this stack.
                    return fallbackCompositeMapping(session, composite);
                }
                items[i] = id;
            }
            // For searching in the tag map.
            Arrays.sort(items);

            List<ItemDescriptorWithCount> tagDescriptor = lookupBedrockTag(session, items);
            if (tagDescriptor != null) {
                return tagDescriptor;
            }

            return fallbackCompositeMapping(session, composite);
        }
        if (slotDisplay instanceof WithRemainderSlotDisplay remainder) {
            // Don't need to worry about what will stay in the crafting table after crafting for the purposes of sending recipes to Bedrock
            return translateToInput(session, remainder.input());
        }
        if (slotDisplay instanceof ItemSlotDisplay itemSlot) {
            return Collections.singletonList(fromItem(session, itemSlot.item()));
        }
        if (slotDisplay instanceof ItemStackSlotDisplay itemStackSlot) {
            ItemData item = ItemTranslator.translateToBedrock(session, itemStackSlot.itemStack());
            return Collections.singletonList(ItemDescriptorWithCount.fromItem(item));
        }
        if (slotDisplay instanceof TagSlotDisplay tagSlot) {
            Key tag = tagSlot.tag();
            int[] items = session.getTagCache().getRaw(new Tag<>(JavaRegistries.ITEM, tag)); // I don't like this...
            if (items == null || items.length == 0) {
                return Collections.singletonList(ItemDescriptorWithCount.EMPTY);
            } else if (items.length == 1) {
                return Collections.singletonList(fromItem(session, items[0]));
            } else {
                // Cache is implemented as, presumably, an item tag will be used multiple times in succession
                // (E.G. a chest with planks tags)
                return TAG_TO_ITEM_DESCRIPTOR_CACHE.get().computeIfAbsent(items, key -> {
                    List<ItemDescriptorWithCount> tagDescriptor = lookupBedrockTag(session, key);
                    if (tagDescriptor != null) {
                        return tagDescriptor;
                    }

                    // In the future, we can probably search through and use subsets of tags as well.
                    // I.E. if a Bedrock tag contains [stone stone_brick] and the Java tag uses [stone stone_brick bricks]
                    // we can still use that Bedrock tag alongside plain item descriptors for "bricks".

                    Set<ItemDescriptorWithCount> itemDescriptors = new HashSet<>();
                    for (int item : key) {
                        itemDescriptors.add(fromItem(session, item));
                    }
                    return List.copyOf(itemDescriptors); // This, or a list from the start with contains -> add?
                });
            }
        }
        GeyserLogger.get().warning("Unimplemented slot display type for input: " + slotDisplay);
        return null;
    }

    private Pair<Item, ItemData> translateToOutput(GeyserSession session, SlotDisplay slotDisplay) {
        if (slotDisplay instanceof EmptySlotDisplay) {
            return null;
        }
        if (slotDisplay instanceof ItemSlotDisplay itemSlot) {
            int item = itemSlot.item();
            return Pair.of(Registries.JAVA_ITEMS.get(item), ItemTranslator.translateToBedrock(session, new ItemStack(item)));
        }
        if (slotDisplay instanceof ItemStackSlotDisplay itemStackSlot) {
            ItemStack stack = itemStackSlot.itemStack();
            return Pair.of(Registries.JAVA_ITEMS.get(stack.getId()), ItemTranslator.translateToBedrock(session, stack));
        }
        GeyserLogger.get().warning("Unimplemented slot display type for output: " + slotDisplay);
        return null;
    }

    private ItemDescriptorWithCount fromItem(GeyserSession session, int item) {
        if (item == Items.AIR_ID) {
            return ItemDescriptorWithCount.EMPTY;
        }
        ItemMapping mapping = session.getItemMappings().getMapping(item);
        return new ItemDescriptorWithCount(new DefaultDescriptor(mapping.getBedrockDefinition(), mapping.getBedrockData()), 1); // Need to check count
    }

    /**
     * Checks to see if this list of items matches with one of this Bedrock version's tags.
     */
    @Nullable
    private List<ItemDescriptorWithCount> lookupBedrockTag(GeyserSession session, int[] items) {
        var bedrockTags = Registries.TAGS.forVersion(session.getUpstream().getProtocolVersion());
        String bedrockTag = bedrockTags.get(items);
        if (bedrockTag != null) {
            return Collections.singletonList(
                new ItemDescriptorWithCount(new ItemTagDescriptor(bedrockTag), 1)
            );
        } else {
            return null;
        }
    }

    /**
     * Converts CompositeSlotDisplay contents to a list of basic ItemDescriptorWithCounts.
     */
    private List<ItemDescriptorWithCount> fallbackCompositeMapping(GeyserSession session, CompositeSlotDisplay composite) {
        return composite.contents().stream()
            .map(subDisplay -> translateToInput(session, subDisplay))
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .toList();
    }

    private Pair<List<List<ItemDescriptorWithCount>>, ItemData> combinations(GeyserSession session, RecipeDisplay display, List<SlotDisplay> ingredients) {
        Pair<Item, ItemData> pair = translateToOutput(session, display.result());
        if (pair == null || !pair.right().isValid()) {
            // Likely modded item Bedrock will complain about
            // Implementation note: ItemData#isValid() may return true for air because count might be > 0 and the air definition may not be ItemDefinition.AIR
            return null;
        }

        ItemData output = pair.right();
        if (!(pair.left() instanceof BedrockRequiresTagItem)) {
            // Strip NBT - tools won't appear in the recipe book otherwise
            output = output.toBuilder().tag(null).build();
        }

        boolean empty = true;
        boolean complexInputs = false;
        List<List<ItemDescriptorWithCount>> inputs = new ArrayList<>(ingredients.size());
        for (SlotDisplay input : ingredients) {
            List<ItemDescriptorWithCount> translated = translateToInput(session, input);
            if (translated == null) {
                continue;
            }
            inputs.add(translated);
            if (translated.size() != 1 || translated.get(0) != ItemDescriptorWithCount.EMPTY) {
                empty = false;
            }
            complexInputs |= translated.size() > 1;
        }
        if (empty) {
            // Crashes Bedrock 1.19.70 otherwise
            // Fixes https://github.com/GeyserMC/Geyser/issues/3549
            return null;
        }

        if (complexInputs) {
            long size = 1;
            // See how big a cartesian product will get without creating one (Guava throws an error; not really ideal)
            for (List<ItemDescriptorWithCount> list : inputs) {
                size *= list.size();
                if (size > 500) {
                    // Too much. No.
                    complexInputs = false;
                    break;
                }
            }
            if (complexInputs) {
                return Pair.of(Lists.cartesianProduct(inputs), output);
            }
        }

        int totalSimpleRecipes = inputs.stream().mapToInt(List::size).max().orElse(1);

        // Sort inputs to create "uniform" simple recipes, if possible
        inputs = inputs.stream()
            .map(descriptors -> descriptors.stream()
                .sorted(ItemDescriptorWithCountComparator.INSTANCE)
                .collect(Collectors.toList()))
            .collect(Collectors.toList());

        List<List<ItemDescriptorWithCount>> finalRecipes = new ArrayList<>(totalSimpleRecipes);
        for (int i = 0; i < totalSimpleRecipes; i++) {
            int current = i;
            finalRecipes.add(inputs.stream().map(descriptors -> {
                if (descriptors.size() > current) {
                    return descriptors.get(current);
                }
                return descriptors.get(0);
            }).toList());
        }

        return Pair.of(finalRecipes, output);
    }

    static class ItemDescriptorWithCountComparator implements Comparator<ItemDescriptorWithCount> {

        static ItemDescriptorWithCountComparator INSTANCE = new ItemDescriptorWithCountComparator();

        @Override
        public int compare(ItemDescriptorWithCount o1, ItemDescriptorWithCount o2) {
            String tag1 = null, tag2 = null;

            // Collect item tags first
            if (o1.getDescriptor() instanceof ItemTagDescriptor itemTagDescriptor) {
                tag1 = itemTagDescriptor.getItemTag();
            }

            if (o2.getDescriptor() instanceof ItemTagDescriptor itemTagDescriptor) {
                tag2 = itemTagDescriptor.getItemTag();
            }

            if (tag1 != null || tag2 != null) {
                if (tag1 != null && tag2 != null) {
                    return tag1.compareTo(tag2); // Just sort based on their string id
                }

                if (tag1 != null) {
                    return -1;
                }

                return 1; // the second is an item tag; which should be r
            }

            if (o1.getDescriptor() instanceof DefaultDescriptor defaultDescriptor1 && o2.getDescriptor() instanceof DefaultDescriptor defaultDescriptor2) {
                return IntComparators.NATURAL_COMPARATOR.compare(defaultDescriptor1.getItemId().getRuntimeId(), defaultDescriptor2.getItemId().getRuntimeId());
            }

            throw new IllegalStateException("Unable to compare unknown item descriptors: " + o1 + " and " + o2);
        }
    }
}
