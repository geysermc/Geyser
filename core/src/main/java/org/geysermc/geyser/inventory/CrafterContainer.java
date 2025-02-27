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

package org.geysermc.geyser.inventory;

import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.CrafterInventoryTranslator;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.jetbrains.annotations.Range;

@Getter
public class CrafterContainer extends Container {
    private GeyserItemStack resultItem = GeyserItemStack.EMPTY;

    @Setter
    private boolean triggered = false;

    /**
     * Bedrock Edition bitmask of the *disabled* slots.
     * Disabled slots are 1, enabled slots are 0 - same as Java Edition
     */
    private short disabledSlotsMask = 0;

    public CrafterContainer(String title, int id, int size, ContainerType containerType, PlayerInventory playerInventory) {
        super(title, id, size, containerType, playerInventory);
    }

    @Override
    public GeyserItemStack getItem(int slot) {
        if (slot == CrafterInventoryTranslator.JAVA_RESULT_SLOT) {
            return this.resultItem;
        } else if (isCraftingGrid(slot)) {
            return super.getItem(slot);
        } else {
            return playerInventory.getItem(slot - CrafterInventoryTranslator.GRID_SIZE + InventoryTranslator.PLAYER_INVENTORY_OFFSET);
        }
    }

    @Override
    public int getOffsetForHotbar(@Range(from = 0, to = 8) int slot) {
        return playerInventory.getOffsetForHotbar(slot) - InventoryTranslator.PLAYER_INVENTORY_OFFSET + CrafterInventoryTranslator.GRID_SIZE;
    }

    @Override
    public void setItem(int slot, @NonNull GeyserItemStack newItem, GeyserSession session) {
        if (slot == CrafterInventoryTranslator.JAVA_RESULT_SLOT) {
            // Result item probably won't be an item that needs to worry about net ID or lodestone compasses
            this.resultItem = newItem;
        } else if (isCraftingGrid(slot)) {
            super.setItem(slot, newItem, session);
        } else {
            playerInventory.setItem(slot - CrafterInventoryTranslator.GRID_SIZE + InventoryTranslator.PLAYER_INVENTORY_OFFSET, newItem, session);
        }
    }

    public void setSlot(int slot, boolean enabled) {
        if (!isCraftingGrid(slot)) {
            GeyserLogger.get().warning("Crafter slot out of bounds: " + slot);
            return;
        }

        if (enabled) {
            disabledSlotsMask = (short) (disabledSlotsMask & ~(1 << slot));
        } else {
            disabledSlotsMask = (short) (disabledSlotsMask | (1 << slot));
        }
    }

    private static boolean isCraftingGrid(int slot) {
        return slot >= 0 && slot <= 8;
    }
}
