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

package org.geysermc.geyser.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.Container;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.LecternContainer;
import org.geysermc.geyser.inventory.click.Click;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserShapedRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserShapelessRecipe;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.cache.tags.Tag;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.inventory.LecternInventoryTranslator;
import org.geysermc.geyser.translator.inventory.chest.DoubleChestInventoryTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.CompositeSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.EmptySlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.TagSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.WithRemainderSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

public class InventoryUtils {
    /**
     * Stores the last used recipe network ID. Since 1.16.200 (and for server-authoritative inventories),
     * each recipe needs a unique network ID (or else in .200 the client crashes).
     */
    public static int LAST_RECIPE_NET_ID;
    
    public static final ItemStack REFRESH_ITEM = new ItemStack(1, 127, new DataComponents(new HashMap<>()));

    public static void openInventory(GeyserSession session, Inventory inventory) {
        session.setOpenInventory(inventory);
        if (session.isClosingInventory() || !session.getUpstream().isInitialized()) {
            // Wait for close confirmation from client before opening the new inventory.
            // Handled in BedrockContainerCloseTranslator
            // or - client hasn't yet loaded in; wait until inventory is shown
            inventory.setPending(true);
            return;
        }
        displayInventory(session, inventory);
    }

    public static void displayInventory(GeyserSession session, Inventory inventory) {
        InventoryTranslator translator = session.getInventoryTranslator();
        if (translator.prepareInventory(session, inventory)) {
            if (translator instanceof DoubleChestInventoryTranslator && !((Container) inventory).isUsingRealBlock()) {
                session.scheduleInEventLoop(() -> {
                    Inventory openInv = session.getOpenInventory();
                    if (openInv != null && openInv.getJavaId() == inventory.getJavaId()) {
                        translator.openInventory(session, inventory);
                        translator.updateInventory(session, inventory);
                        openInv.setDisplayed(true);
                    } else if (openInv != null && openInv.isPending()) {
                        // Presumably, this inventory is no longer relevant, and the client doesn't care about it
                        displayInventory(session, openInv);
                    }
                }, 200, TimeUnit.MILLISECONDS);
            } else {
                translator.openInventory(session, inventory);
                translator.updateInventory(session, inventory);
                inventory.setDisplayed(true);
            }
        } else {
            // Can occur if we e.g. did not find a spot to put a fake container in
            sendJavaContainerClose(session, inventory);
            session.setOpenInventory(null);
            session.setInventoryTranslator(InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR);
        }
    }

    public static void closeInventory(GeyserSession session, int javaId, boolean confirm) {
        session.getPlayerInventory().setCursor(GeyserItemStack.EMPTY, session);
        updateCursor(session);

        Inventory inventory = getInventory(session, javaId);
        if (inventory != null) {
            InventoryTranslator translator = session.getInventoryTranslator();
            translator.closeInventory(session, inventory);
            if (confirm && inventory.isDisplayed() && !inventory.isPending()
                    && !(translator instanceof LecternInventoryTranslator) // Closing lecterns is not followed with a close confirmation
            ) {
                session.setClosingInventory(true);
            }
            session.getBundleCache().onInventoryClose(inventory);
        }
        session.setInventoryTranslator(InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR);
        session.setOpenInventory(null);
    }

    public static @Nullable Inventory getInventory(GeyserSession session, int javaId) {
        if (javaId == 0) {
            // ugly hack: lecterns aren't their own inventory on Java, and can hence be closed with e.g. an id of 0
            if (session.getOpenInventory() instanceof LecternContainer) {
                return session.getOpenInventory();
            }
            return session.getPlayerInventory();
        } else {
            Inventory openInventory = session.getOpenInventory();
            if (openInventory != null && javaId == openInventory.getJavaId()) {
                return openInventory;
            }
            return null;
        }
    }

    public static void sendJavaContainerClose(GeyserSession session, Inventory inventory) {
        if (inventory.shouldConfirmContainerClose()) {
            ServerboundContainerClosePacket closeWindowPacket = new ServerboundContainerClosePacket(inventory.getJavaId());
            session.sendDownstreamGamePacket(closeWindowPacket);
        }
    }

    /**
     * Finds a usable block space in the world to place a fake inventory block, and returns the position.
     */
    @Nullable
    public static Vector3i findAvailableWorldSpace(GeyserSession session) {
        // Check if a fake block can be placed, either above the player or beneath.
        BedrockDimension dimension = session.getBedrockDimension();
        int minY = dimension.minY(), maxY = minY + dimension.height();
        Vector3i flatPlayerPosition = session.getPlayerEntity().getPosition().toInt();
        Vector3i position = flatPlayerPosition.add(Vector3i.UP);
        if (position.getY() < minY) {
            return null;
        }
        if (position.getY() >= maxY || !canUseWorldSpace(session, position)) {
            position = flatPlayerPosition.sub(0, 4, 0);
            if (position.getY() >= maxY || !canUseWorldSpace(session, position)) {
                return null;
            }
        }
        return position;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean canUseWorldSpace(GeyserSession session, Vector3i position) {
        BlockState state = session.getGeyser().getWorldManager().blockAt(session, position);
        // Block entities require more data to be restored; so let's avoid using these positions
        return state.block().blockEntityType() == null;
    }

    public static void updateCursor(GeyserSession session) {
        InventorySlotPacket cursorPacket = new InventorySlotPacket();
        cursorPacket.setContainerId(ContainerId.UI);
        cursorPacket.setSlot(0);
        cursorPacket.setItem(session.getPlayerInventory().getCursor().getItemData(session));
        session.sendUpstreamPacket(cursorPacket);
    }

    public static boolean canStack(GeyserItemStack item1, GeyserItemStack item2) {
        if (GeyserImpl.getInstance().getConfig().isDebugMode())
            canStackDebug(item1, item2);
        if (item1.isEmpty() || item2.isEmpty())
            return false;
        return item1.getJavaId() == item2.getJavaId() && Objects.equals(item1.getComponents(), item2.getComponents());
    }

    private static void canStackDebug(GeyserItemStack item1, GeyserItemStack item2) {
        DataComponents components1 = item1.getComponents();
        DataComponents components2 = item2.getComponents();
        if (components1 != null && components2 != null) {
            if (components1.hashCode() == components2.hashCode() && !components1.equals(components2)) {
                GeyserLogger.get().error("DEBUG: DataComponents hash collision");
                GeyserLogger.get().error("hash: " + components1.hashCode());
                GeyserLogger.get().error("components1: " + components1);
                GeyserLogger.get().error("components2: " + components2);
            }
        }
    }

    /**
     * Checks to see if an item stack represents air or has no count.
     */
    @Contract("null -> true")
    public static boolean isEmpty(@Nullable ItemStack itemStack) {
        return itemStack == null || itemStack.getId() == Items.AIR_ID || itemStack.getAmount() <= 0;
    }

    /**
     * Returns a barrier block with custom name and lore to explain why
     * part of the inventory is unusable.
     *
     * @param description the description
     * @return the unusable space block
     */
    public static IntFunction<ItemData> createUnusableSpaceBlock(String description) {
        NbtMapBuilder root = NbtMap.builder();
        NbtMapBuilder display = NbtMap.builder();

        // Not ideal to use log here but we dont get a session
        display.putString("Name", ChatColor.RESET + GeyserLocale.getLocaleStringLog("geyser.inventory.unusable_item.name"));
        display.putList("Lore", NbtType.STRING, Collections.singletonList(ChatColor.RESET + ChatColor.DARK_PURPLE + description));

        root.put("display", display.build());
        return protocolVersion -> ItemData.builder()
                .definition(getUnusableSpaceBlockDefinition(protocolVersion))
                .count(1)
                .tag(root.build()).build();
    }

    private static ItemDefinition getUnusableSpaceBlockDefinition(int protocolVersion) {
        ItemMappings mappings = Registries.ITEMS.forVersion(protocolVersion);
        String unusableSpaceBlock = GeyserImpl.getInstance().getConfig().getUnusableSpaceBlock();
        ItemDefinition itemDefinition = mappings.getDefinition(unusableSpaceBlock);

        if (itemDefinition == null) {
            GeyserLogger.get().error("Invalid value " + unusableSpaceBlock + ". Resorting to barrier block.");
            return mappings.getStoredItems().barrier().getBedrockDefinition();
        } else {
            return itemDefinition;
        }
    }

    public static IntFunction<ItemData> getUpgradeTemplate() {
        return protocolVersion -> ItemData.builder()
                .definition(Registries.ITEMS.forVersion(protocolVersion).getStoredItems().upgradeTemplate().getBedrockDefinition())
                .count(1).build();
    }

    public static IntFunction<ItemData> getTotemOfUndying() {
        return protocolVersion -> ItemData.builder()
            .definition(Registries.ITEMS.forVersion(protocolVersion).getStoredItems().totem().getBedrockDefinition())
            .count(1).build();
    }

    @Nullable
    public static Click getClickForHotbarSwap(int slot) {
        return switch (slot) {
            case 0 -> Click.SWAP_TO_HOTBAR_1;
            case 1 -> Click.SWAP_TO_HOTBAR_2;
            case 2 -> Click.SWAP_TO_HOTBAR_3;
            case 3 -> Click.SWAP_TO_HOTBAR_4;
            case 4 -> Click.SWAP_TO_HOTBAR_5;
            case 5 -> Click.SWAP_TO_HOTBAR_6;
            case 6 -> Click.SWAP_TO_HOTBAR_7;
            case 7 -> Click.SWAP_TO_HOTBAR_8;
            case 8 -> Click.SWAP_TO_HOTBAR_9;
            default -> null;
        };
    }

    /**
     * Returns if the provided item stack would be accepted by the slot display.
     */
    public static boolean acceptsAsInput(GeyserSession session, SlotDisplay slotDisplay, GeyserItemStack itemStack) {
        if (slotDisplay instanceof EmptySlotDisplay) {
            return itemStack.isEmpty();
        }
        if (slotDisplay instanceof CompositeSlotDisplay compositeSlotDisplay) {
            if (compositeSlotDisplay.contents().size() == 1) {
                return acceptsAsInput(session, compositeSlotDisplay.contents().get(0), itemStack);
            }
            return compositeSlotDisplay.contents().stream().anyMatch(aSlotDisplay -> acceptsAsInput(session, aSlotDisplay, itemStack));
        }
        if (slotDisplay instanceof WithRemainderSlotDisplay remainderSlotDisplay) {
            return acceptsAsInput(session, remainderSlotDisplay.input(), itemStack);
        }
        if (slotDisplay instanceof ItemSlotDisplay itemSlotDisplay) {
            return itemStack.getJavaId() == itemSlotDisplay.item();
        }
        if (slotDisplay instanceof ItemStackSlotDisplay itemStackSlotDisplay) {
            ItemStack other = itemStackSlotDisplay.itemStack();
            // Amount check might be flimsy?
            return itemStack.getJavaId() == other.getId() && itemStack.getAmount() >= other.getAmount()
                && Objects.equals(itemStack.getComponents(), other.getDataComponentsPatch());
        }
        if (slotDisplay instanceof TagSlotDisplay tagSlotDisplay) {
            return session.getTagCache().is(new Tag<>(JavaRegistries.ITEM, tagSlotDisplay.tag()), itemStack.asItem());
        }
        GeyserLogger.get().warning("Unknown slot display type: " + slotDisplay);
        return false;
    }

    /**
     * Test all known recipes to find a valid match
     *
     * @param output if not null, the recipe has to output this item
     */
    @Nullable
    public static GeyserRecipe getValidRecipe(final GeyserSession session, final @Nullable ItemStack output, final IntFunction<GeyserItemStack> inventoryGetter,
                                        final int gridDimensions, final int firstRow, final int height, final int firstCol, final int width) {
        int nonAirCount = 0; // Used for shapeless recipes for amount of items needed in recipe
        for (int row = firstRow; row < height + firstRow; row++) {
            for (int col = firstCol; col < width + firstCol; col++) {
                if (!inventoryGetter.apply(col + (row * gridDimensions) + 1).isEmpty()) {
                    nonAirCount++;
                }
            }
        }

        recipes:
        for (GeyserRecipe recipe : session.getCraftingRecipes().values()) {
            if (recipe.isShaped()) {
                GeyserShapedRecipe shapedRecipe = (GeyserShapedRecipe) recipe;
                if (output != null && !acceptsAsInput(session, shapedRecipe.result(), GeyserItemStack.from(output))) {
                    continue;
                }
                List<SlotDisplay> ingredients = shapedRecipe.ingredients();
                if (shapedRecipe.width() != width || shapedRecipe.height() != height || width * height != ingredients.size()) {
                    continue;
                }

                if (!testShapedRecipe(session, ingredients, inventoryGetter, gridDimensions, firstRow, height, firstCol, width)) {
                    List<SlotDisplay> mirroredIngredients = new ArrayList<>(ingredients.size());
                    for (int row = 0; row < height; row++) {
                        for (int col = 0; col < width; col++) {
                            int index = col + (row * width);
                            while (mirroredIngredients.size() <= index) {
                                mirroredIngredients.add(null);
                            }
                            mirroredIngredients.set(index, ingredients.get((width - 1 - col) + (row * width)));
                        }
                    }

                    if (ingredients.equals(mirroredIngredients) ||
                            !testShapedRecipe(session, mirroredIngredients, inventoryGetter, gridDimensions, firstRow, height, firstCol, width)) {
                        continue;
                    }
                }
            } else {
                GeyserShapelessRecipe data = (GeyserShapelessRecipe) recipe;
                if (output != null && !acceptsAsInput(session, data.result(), GeyserItemStack.from(output))) {
                    continue;
                }
                if (nonAirCount != data.ingredients().size()) {
                    // There is an amount of items on the crafting table that is not the same as the ingredient count so this is invalid
                    continue;
                }
                for (int i = 0; i < data.ingredients().size(); i++) {
                    SlotDisplay slotDisplay = data.ingredients().get(i);
                    boolean inventoryHasItem = false;
                    // Iterate only over the crafting table to find this item
                    for (int row = firstRow; row < height + firstRow; row++) {
                        for (int col = firstCol; col < width + firstCol; col++) {
                            GeyserItemStack geyserItemStack = inventoryGetter.apply(col + (row * gridDimensions) + 1);
                            if (acceptsAsInput(session, slotDisplay, geyserItemStack)) {
                                inventoryHasItem = true;
                                break;
                            }
                        }
                    }
                    if (!inventoryHasItem) {
                        continue recipes;
                    }
                }
            }
            return recipe;
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean testShapedRecipe(final GeyserSession session, final List<SlotDisplay> ingredients, final IntFunction<GeyserItemStack> inventoryGetter,
                                            final int gridDimensions, final int firstRow, final int height, final int firstCol, final int width) {
        int ingredientIndex = 0;
        for (int row = firstRow; row < height + firstRow; row++) {
            for (int col = firstCol; col < width + firstCol; col++) {
                GeyserItemStack geyserItemStack = inventoryGetter.apply(col + (row * gridDimensions) + 1);
                SlotDisplay slotDisplay = ingredients.get(ingredientIndex++);
                if (!acceptsAsInput(session, slotDisplay, geyserItemStack)) {
                    return false;
                }
            }
        }
        return true;
    }
}
