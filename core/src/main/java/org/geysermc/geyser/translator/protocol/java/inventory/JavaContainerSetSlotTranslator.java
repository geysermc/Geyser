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

package org.geysermc.geyser.translator.protocol.java.inventory;

import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.recipe.GeyserShapedRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserSmithingRecipe;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.inventory.PlayerInventoryTranslator;
import org.geysermc.geyser.translator.inventory.SmithingInventoryTranslator;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Translator(packet = ClientboundContainerSetSlotPacket.class)
public class JavaContainerSetSlotTranslator extends PacketTranslator<ClientboundContainerSetSlotPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundContainerSetSlotPacket packet) {
        //TODO: support window id -2, should update player inventory
        //TODO: ^ I think this is outdated.
        Inventory inventory = InventoryUtils.getInventory(session, packet.getContainerId());
        if (inventory == null) {
            return;
        }

        InventoryTranslator translator = session.getInventoryTranslator();
        if (translator != null) {
            int slot = packet.getSlot();
            if (slot >= inventory.getSize()) {
                GeyserLogger logger = GeyserLogger.get();
                logger.warning("ClientboundContainerSetSlotPacket sent to " + session.bedrockUsername()
                        + " that exceeds inventory size!");
                if (logger.isDebug()) {
                    logger.debug(packet.toString());
                    logger.debug(inventory.toString());
                }
                // 1.19.0 behavior: the state ID will not be set due to exception
                return;
            }

            if (translator instanceof SmithingInventoryTranslator) {
                updateSmithingTableOutput(session, slot, packet.getItem(), inventory);
            } else {
                updateCraftingGrid(session, slot, packet.getItem(), inventory, translator);
            }

            GeyserItemStack newItem = GeyserItemStack.from(packet.getItem());
            session.getBundleCache().initialize(newItem);
            if (packet.getContainerId() == 0 && !(translator instanceof PlayerInventoryTranslator)) {
                // In rare cases, the window ID can still be 0 but Java treats it as valid
                // This behavior still exists as of Java Edition 1.21.2, despite the new packet
                session.getPlayerInventory().setItem(slot, newItem, session);
                InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, session.getPlayerInventory(), slot);
            } else {
                inventory.setItem(slot, newItem, session);
                translator.updateSlot(session, inventory, slot);
            }

            // Intentional behavior here below the cursor; Minecraft 1.18.1 also does this.
            int stateId = packet.getStateId();
            session.setEmulatePost1_16Logic(stateId > 0 || stateId != inventory.getStateId());
            inventory.setStateId(stateId);
        }
    }

    /**
     * Checks for a changed output slot in the crafting grid, and ensures Bedrock sees the recipe.
     */
    private static void updateCraftingGrid(GeyserSession session, int slot, ItemStack item, Inventory inventory, InventoryTranslator translator) {
        // Check if it's the crafting grid result slot.
        if (slot != 0) {
            return;
        }

        // Check if there is any crafting grid.
        int gridSize = translator.getGridSize();
        if (gridSize == -1) {
            return;
        }

        // Only process the most recent crafting grid result, and cancel the previous one.
        if (session.getContainerOutputFuture() != null) {
            session.getContainerOutputFuture().cancel(false);
        }

        if (InventoryUtils.isEmpty(item)) {
            return;
        }

        session.setContainerOutputFuture(session.scheduleInEventLoop(() -> {
            int offset = gridSize == 4 ? 28 : 32;
            int gridDimensions = gridSize == 4 ? 2 : 3;
            int firstRow = -1, height = -1;
            int firstCol = -1, width = -1;
            for (int row = 0; row < gridDimensions; row++) {
                for (int col = 0; col < gridDimensions; col++) {
                    if (!inventory.getItem(col + (row * gridDimensions) + 1).isEmpty()) {
                        if (firstRow == -1) {
                            firstRow = row;
                            firstCol = col;
                        } else {
                            firstCol = Math.min(firstCol, col);
                        }
                        height = Math.max(height, row);
                        width = Math.max(width, col);
                    }
                }
            }

            //empty grid
            if (firstRow == -1) {
                return;
            }

            height += -firstRow + 1;
            width += -firstCol + 1;

            if (InventoryUtils.getValidRecipe(session, item, inventory::getItem, gridDimensions, firstRow,
                    height, firstCol, width) != null) {
                // Recipe is already present on the client; don't send packet
                return;
            }

            UUID uuid = UUID.randomUUID();
            int newRecipeId = session.getLastRecipeNetId().incrementAndGet();

            ItemData[] ingredients = new ItemData[height * width];
            //construct ingredient list and clear slots on client
            List<SlotDisplay> javaIngredients = new ArrayList<>(height * width);
            int index = 0;
            for (int row = firstRow; row < height + firstRow; row++) {
                for (int col = firstCol; col < width + firstCol; col++) {
                    GeyserItemStack geyserItemStack = inventory.getItem(col + (row * gridDimensions) + 1);
                    ingredients[index] = geyserItemStack.getItemData(session);
                    javaIngredients.add(geyserItemStack.asSlotDisplay());

                    InventorySlotPacket slotPacket = new InventorySlotPacket();
                    slotPacket.setContainerId(ContainerId.UI);
                    slotPacket.setSlot(col + (row * gridDimensions) + offset);
                    slotPacket.setItem(ItemData.AIR);
                    session.sendUpstreamPacket(slotPacket);
                    index++;
                }
            }

            // Cache this recipe so we know the client has received it
            session.getCraftingRecipes().put(newRecipeId, new GeyserShapedRecipe(width, height, javaIngredients, new ItemStackSlotDisplay(item)));

            CraftingDataPacket craftPacket = new CraftingDataPacket();
            craftPacket.getCraftingData().add(ShapedRecipeData.shaped(
                    uuid.toString(),
                    width,
                    height,
                    Arrays.stream(ingredients).map(ItemDescriptorWithCount::fromItem).toList(),
                    Collections.singletonList(ItemTranslator.translateToBedrock(session, item)),
                    uuid,
                    "crafting_table",
                    0,
                    newRecipeId,
                    false,
                    RecipeUnlockingRequirement.INVALID
            ));
            craftPacket.setCleanRecipes(false);
            session.sendUpstreamPacket(craftPacket);

            index = 0;
            for (int row = firstRow; row < height + firstRow; row++) {
                for (int col = firstCol; col < width + firstCol; col++) {
                    InventorySlotPacket slotPacket = new InventorySlotPacket();
                    slotPacket.setContainerId(ContainerId.UI);
                    slotPacket.setSlot(col + (row * gridDimensions) + offset);
                    slotPacket.setItem(ingredients[index]);
                    session.sendUpstreamPacket(slotPacket);
                    index++;
                }
            }
        }, 150, TimeUnit.MILLISECONDS));
    }

    static void updateSmithingTableOutput(GeyserSession session, int slot, ItemStack output, Inventory inventory) {
        if (slot != SmithingInventoryTranslator.OUTPUT) {
            return;
        }

        // Only process the most recent output result, and cancel the previous one.
        if (session.getContainerOutputFuture() != null) {
            session.getContainerOutputFuture().cancel(false);
        }

        if (InventoryUtils.isEmpty(output)) {
            return;
        }

        session.setContainerOutputFuture(session.scheduleInEventLoop(() -> {
            GeyserItemStack template = inventory.getItem(SmithingInventoryTranslator.TEMPLATE);
            if (template.asItem() != Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
                // Technically we should probably also do this for custom items, but last I checked Bedrock doesn't even support that.
                return;
            }

            GeyserItemStack input = inventory.getItem(SmithingInventoryTranslator.INPUT);
            GeyserItemStack material = inventory.getItem(SmithingInventoryTranslator.MATERIAL);
            GeyserItemStack geyserOutput = GeyserItemStack.from(output);

            for (GeyserSmithingRecipe recipe : session.getSmithingRecipes()) {
                if (InventoryUtils.acceptsAsInput(session, recipe.result(), geyserOutput)
                && InventoryUtils.acceptsAsInput(session, recipe.base(), input)
                && InventoryUtils.acceptsAsInput(session, recipe.addition(), material)
                && InventoryUtils.acceptsAsInput(session, recipe.template(), template)) {
                    // The client already recognizes this item.
                    return;
                }
            }

            session.getSmithingRecipes().add(new GeyserSmithingRecipe(
                template.asSlotDisplay(),
                input.asSlotDisplay(),
                material.asSlotDisplay(),
                new ItemStackSlotDisplay(output)
            ));

            UUID uuid = UUID.randomUUID();

            CraftingDataPacket craftPacket = new CraftingDataPacket();
            craftPacket.getCraftingData().add(SmithingTransformRecipeData.of(
                uuid.toString(),
                ItemDescriptorWithCount.fromItem(ItemTranslator.translateToBedrock(session, template.getItemStack())),
                ItemDescriptorWithCount.fromItem(ItemTranslator.translateToBedrock(session, input.getItemStack())),
                ItemDescriptorWithCount.fromItem(ItemTranslator.translateToBedrock(session, material.getItemStack())),
                ItemTranslator.translateToBedrock(session, output),
                "smithing_table",
                session.getLastRecipeNetId().incrementAndGet()
            ));
            craftPacket.setCleanRecipes(false);
            session.sendUpstreamPacket(craftPacket);

            // Just set one of the slots to air, then right back to its proper item.
            InventorySlotPacket slotPacket = new InventorySlotPacket();
            slotPacket.setContainerId(ContainerId.UI);
            slotPacket.setSlot(session.getInventoryTranslator().javaSlotToBedrock(SmithingInventoryTranslator.MATERIAL));
            slotPacket.setItem(ItemData.AIR);
            session.sendUpstreamPacket(slotPacket);

            session.getInventoryTranslator().updateSlot(session, inventory, SmithingInventoryTranslator.MATERIAL);
        }, 150, TimeUnit.MILLISECONDS));
    }
}
