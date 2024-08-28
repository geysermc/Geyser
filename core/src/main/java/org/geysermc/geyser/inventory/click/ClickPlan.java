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

package org.geysermc.geyser.inventory.click;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import it.unimi.dsi.fastutil.ints.*;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.SlotType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.CraftingInventoryTranslator;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.util.InventoryUtils;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public final class ClickPlan {
    private final List<ClickAction> plan = new ArrayList<>();
    private final Int2ObjectMap<GeyserItemStack> simulatedItems;
    /**
     * Used for 1.17.1+ proper packet translation - any non-cursor item that is changed in a single transaction gets sent here.
     */
    private Int2ObjectMap<ItemStack> changedItems;
    private GeyserItemStack simulatedCursor;
    private boolean finished;

    private final GeyserSession session;
    private final InventoryTranslator translator;
    private final Inventory inventory;
    private final int gridSize;

    public ClickPlan(GeyserSession session, InventoryTranslator translator, Inventory inventory) {
        this.session = session;
        this.translator = translator;
        this.inventory = inventory;

        this.simulatedItems = new Int2ObjectOpenHashMap<>(inventory.getSize());
        this.changedItems = null;
        this.simulatedCursor = session.getPlayerInventory().getCursor().copy();
        this.finished = false;

        gridSize = translator.getGridSize();
    }

    private void resetSimulation() {
        this.simulatedItems.clear();
        this.simulatedCursor = session.getPlayerInventory().getCursor().copy();
    }

    public void add(Click click, int slot) {
        add(click, slot, false);
    }

    public void add(Click click, int slot, boolean force) {
        if (finished)
            throw new UnsupportedOperationException("ClickPlan already executed");

        if (click == Click.LEFT_OUTSIDE || click == Click.RIGHT_OUTSIDE) {
            slot = Click.OUTSIDE_SLOT;
        }

        ClickAction action = new ClickAction(click, slot, force);
        plan.add(action);
        // RUNNING THE SIMULATION HERE IS IMPORTANT. The contents of the simulation are used in complex, multi-stage tasks
        // such as autocrafting.
        simulateAction(action);
    }

    public void execute(boolean refresh) {
        //update geyser inventory after simulation to avoid net id desync
        resetSimulation();
        ListIterator<ClickAction> planIter = plan.listIterator();
        while (planIter.hasNext()) {
            ClickAction action = planIter.next();

            if (action.slot != Click.OUTSIDE_SLOT && translator.getSlotType(action.slot) != SlotType.NORMAL) {
                // Needed with Paper 1.16.5
                refresh = true;
            }

            changedItems = new Int2ObjectOpenHashMap<>();

            boolean emulatePost1_16Logic = session.isEmulatePost1_16Logic();

            int stateId;
            if (emulatePost1_16Logic) {
                stateId = stateIdHack(action);
                simulateAction(action);
            } else {
                stateId = inventory.getStateId();
            }

            ItemStack clickedItemStack;
            if (emulatePost1_16Logic) {
                // The action must be simulated first as Java expects the new contents of the cursor (as of 1.18.1)
                clickedItemStack = simulatedCursor.getItemStack();
            } else {
                if (!planIter.hasNext() && refresh) {
                    // Doesn't have the intended effect with state IDs since this won't cause a complete window refresh
                    // (It will eventually once state IDs desync, but this causes more problems than not)
                    clickedItemStack = InventoryUtils.REFRESH_ITEM;
                } else {
                    if (action.click.actionType == ContainerActionType.DROP_ITEM || action.slot == Click.OUTSIDE_SLOT) {
                        clickedItemStack = null;
                    } else {
                        clickedItemStack = getItem(action.slot).getItemStack();
                    }
                }
            }

            if (!emulatePost1_16Logic) {
                simulateAction(action);
            }

            ServerboundContainerClickPacket clickPacket = new ServerboundContainerClickPacket(
                    inventory.getJavaId(),
                    stateId,
                    action.slot,
                    action.click.actionType,
                    action.click.action,
                    clickedItemStack,
                    changedItems
            );

            session.sendDownstreamGamePacket(clickPacket);
        }

        session.getPlayerInventory().setCursor(simulatedCursor, session);
        for (Int2ObjectMap.Entry<GeyserItemStack> simulatedSlot : simulatedItems.int2ObjectEntrySet()) {
            inventory.setItem(simulatedSlot.getIntKey(), simulatedSlot.getValue(), session);
        }
        finished = true;
    }

    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Test if the item stacks with another item in the specified slot.
     * This will check the simulated inventory without copying.
     */
    public boolean canStack(int slot, GeyserItemStack item) {
        GeyserItemStack slotItem = simulatedItems.getOrDefault(slot, inventory.getItem(slot));
        return InventoryUtils.canStack(slotItem, item);
    }

    /**
     * Test if the specified slot is empty.
     * This will check the simulated inventory without copying.
     */
    public boolean isEmpty(int slot) {
        return simulatedItems.getOrDefault(slot, inventory.getItem(slot)).isEmpty();
    }

    public GeyserItemStack getItem(int slot) {
        return simulatedItems.computeIfAbsent(slot, k -> inventory.getItem(slot).copy());
    }

    public GeyserItemStack getCursor() {
        return simulatedCursor;
    }

    private void setItem(int slot, GeyserItemStack item) {
        simulatedItems.put(slot, item);
        onSlotItemChange(slot, item);
    }

    private void setCursor(GeyserItemStack item) {
        simulatedCursor = item;
    }

    private void add(int slot, GeyserItemStack itemStack, int amount) {
        itemStack.add(amount);
        onSlotItemChange(slot, itemStack);
    }

    private void sub(int slot, GeyserItemStack itemStack, int amount) {
        itemStack.sub(amount);
        onSlotItemChange(slot, itemStack);
    }

    private void setAmount(int slot, GeyserItemStack itemStack, int amount) {
        itemStack.setAmount(amount);
        onSlotItemChange(slot, itemStack);
    }

    /**
     * Does not need to be called for the cursor
     */
    private void onSlotItemChange(int slot, GeyserItemStack itemStack) {
        if (changedItems != null) {
            changedItems.put(slot, itemStack.getItemStack());
        }
    }

    private void simulateAction(ClickAction action) {
        GeyserItemStack cursor = getCursor();
        switch (action.click) {
            case LEFT_OUTSIDE -> {
                setCursor(GeyserItemStack.EMPTY);
                return;
            }
            case RIGHT_OUTSIDE -> {
                if (!cursor.isEmpty()) {
                    cursor.sub(1);
                }
                return;
            }
        }

        GeyserItemStack clicked = getItem(action.slot);
        if (translator.getSlotType(action.slot) == SlotType.OUTPUT) {
            switch (action.click) {
                case LEFT, RIGHT -> {
                    if (cursor.isEmpty() && !clicked.isEmpty()) {
                        setCursor(clicked.copy());
                    } else if (InventoryUtils.canStack(cursor, clicked)) {
                        cursor.add(clicked.getAmount());
                    }
                    reduceCraftingGrid(false);
                    setItem(action.slot, GeyserItemStack.EMPTY); // Matches Java Edition 1.18.1
                }
                case LEFT_SHIFT -> reduceCraftingGrid(true);
            }
        } else {
            switch (action.click) {
                case LEFT:
                    if (!InventoryUtils.canStack(cursor, clicked)) {
                        setCursor(clicked);
                        setItem(action.slot, cursor);
                    } else {
                        setCursor(GeyserItemStack.EMPTY);
                        add(action.slot, clicked, cursor.getAmount());
                    }
                    break;
                case RIGHT:
                    if (cursor.isEmpty() && !clicked.isEmpty()) {
                        int half = clicked.getAmount() / 2; //smaller half
                        setCursor(clicked.copy(clicked.getAmount() - half)); //larger half
                        setAmount(action.slot, clicked, half);
                    } else if (!cursor.isEmpty() && clicked.isEmpty()) {
                        cursor.sub(1);
                        setItem(action.slot, cursor.copy(1));
                    } else if (InventoryUtils.canStack(cursor, clicked)) {
                        cursor.sub(1);
                        add(action.slot, clicked, 1);
                    }
                    break;
                case SWAP_TO_HOTBAR_1:
                    swap(action.slot, inventory.getOffsetForHotbar(0), clicked);
                    break;
                case SWAP_TO_HOTBAR_2:
                    swap(action.slot, inventory.getOffsetForHotbar(1), clicked);
                    break;
                case SWAP_TO_HOTBAR_3:
                    swap(action.slot, inventory.getOffsetForHotbar(2), clicked);
                    break;
                case SWAP_TO_HOTBAR_4:
                    swap(action.slot, inventory.getOffsetForHotbar(3), clicked);
                    break;
                case SWAP_TO_HOTBAR_5:
                    swap(action.slot, inventory.getOffsetForHotbar(4), clicked);
                    break;
                case SWAP_TO_HOTBAR_6:
                    swap(action.slot, inventory.getOffsetForHotbar(5), clicked);
                    break;
                case SWAP_TO_HOTBAR_7:
                    swap(action.slot, inventory.getOffsetForHotbar(6), clicked);
                    break;
                case SWAP_TO_HOTBAR_8:
                    swap(action.slot, inventory.getOffsetForHotbar(7), clicked);
                    break;
                case SWAP_TO_HOTBAR_9:
                    swap(action.slot, inventory.getOffsetForHotbar(8), clicked);
                    break;
                case LEFT_SHIFT:
                    //TODO
                    break;
                case DROP_ONE:
                    if (!clicked.isEmpty()) {
                        sub(action.slot, clicked, 1);
                    }
                    break;
                case DROP_ALL:
                    setItem(action.slot, GeyserItemStack.EMPTY);
                    break;
            }
        }
    }

    /**
     * Swap between two inventory slots without a cursor. This should only be used with {@link ContainerActionType#MOVE_TO_HOTBAR_SLOT}
     */
    private void swap(int sourceSlot, int destSlot, GeyserItemStack sourceItem) {
        GeyserItemStack destinationItem = getItem(destSlot);
        setItem(sourceSlot, destinationItem);
        setItem(destSlot, sourceItem);
    }

    private int stateIdHack(ClickAction action) {
        int stateId;
        if (inventory.getNextStateId() != -1) {
            stateId = inventory.getNextStateId();
        } else {
            stateId = inventory.getStateId();
        }

        // Java will never ever send more than one container click packet per set of actions*.
        // *(exception being Java's "quick craft"/painting feature)
        // Bedrock might, and this would generally fall into one of two categories:
        // - Bedrock is sending an item directly from one slot to another, without picking it up, that cannot
        //   be expressed with a shift click
        // - Bedrock wants to pick up or place an arbitrary amount of items that cannot be expressed from
        //   one left/right click action.
        // Java typically doesn't increment the state ID if you send a vanilla-accurate container click packet,
        // but it will increment the state ID with a vanilla client in at least the crafting table
        if (inventory.getContainerType() == ContainerType.CRAFTING && CraftingInventoryTranslator.isCraftingGrid(action.slot)) {
            // 1.18.1 sends a second set slot update for any action in the crafting grid
            // And an additional packet if something is removed (Mojmap: CraftingContainer#removeItem)
            int stateIdIncrements;
            GeyserItemStack clicked = getItem(action.slot);
            if (action.click == Click.LEFT) {
                if (!clicked.isEmpty() && !InventoryUtils.canStack(simulatedCursor, clicked)) {
                    // An item is removed from the crafting table; yes deletion
                    stateIdIncrements = 2;
                } else {
                    // We can stack and we add all the items to the crafting slot; no deletion
                    stateIdIncrements = 1;
                }
            } else if (action.click == Click.RIGHT) {
                stateIdIncrements = 1;
            } else if (action.click.actionType == ContainerActionType.MOVE_TO_HOTBAR_SLOT) {
                stateIdIncrements = 1;
            } else {
                if (session.getGeyser().getConfig().isDebugMode()) {
                    session.getGeyser().getLogger().debug("Not sure how to handle state ID hack in crafting table: " + plan);
                }
                stateIdIncrements = 1;
            }
            inventory.incrementStateId(stateIdIncrements);
        }

        return stateId;
    }

    private void reduceCraftingGrid(boolean makeAll) {
        if (gridSize == -1)
            return;

        int crafted;
        if (!makeAll) {
            crafted = 1;
        } else {
            crafted = 0;
            for (int i = 0; i < gridSize; i++) {
                GeyserItemStack item = getItem(i + 1);
                if (!item.isEmpty()) {
                    if (crafted == 0) {
                        crafted = item.getAmount();
                    }
                    crafted = Math.min(crafted, item.getAmount());
                }
            }
        }

        for (int i = 0; i < gridSize; i++) {
            final int slot = i + 1;
            GeyserItemStack item = getItem(slot);
            if (!item.isEmpty()) {
                // These changes should be broadcasted to the server
                sub(slot, item, crafted);
            }
        }
    }

    /**
     * @return a new set of all affected slots.
     */
    @Contract("-> new")
    public IntSet getAffectedSlots() {
        IntSet affectedSlots = new IntOpenHashSet();
        for (ClickAction action : plan) {
            if (translator.getSlotType(action.slot) != SlotType.OUTPUT && action.slot != Click.OUTSIDE_SLOT) {
                affectedSlots.add(action.slot);
                if (action.click.actionType == ContainerActionType.MOVE_TO_HOTBAR_SLOT) {
                    //TODO won't work if offhand is added
                    affectedSlots.add(inventory.getOffsetForHotbar(((MoveToHotbarAction) action.click.action).ordinal()));
                }
            }
        }
        return affectedSlots;
    }

    private record ClickAction(Click click, int slot, boolean force) {
    }
}
