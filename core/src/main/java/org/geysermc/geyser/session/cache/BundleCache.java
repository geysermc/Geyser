/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.ContainerRegistryCleanupPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.tags.ItemTag;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BundleCache {
    private static final int BUNDLE_CONTAINER_ID = 125; // BDS 1.21.44
    private final GeyserSession session;
    private int nextBundleId;

    private int releaseTick = -1;

    public BundleCache(GeyserSession session) {
        this.session = session;
    }

    /**
     * Checks to see if the given item from the server is a bundle.
     * If so, we initialize our bundle cache.
     */
    public void initialize(GeyserItemStack itemStack) {
        // Message before 1.21.4 - "Can't check for BUNDLE_CONTENTS, which may be missing if the bundle is empty."
        // Now irrelevant, but keeping as-is for the time being.
        if (session.getTagCache().is(ItemTag.BUNDLES, itemStack)) {
            if (itemStack.getBundleData() != null) {
                session.getGeyser().getLogger().warning("Stack has bundle data already! It should not!");
                if (session.getGeyser().getLogger().isDebug()) {
                    session.getGeyser().getLogger().debug("Player: " + session.javaUsername());
                    session.getGeyser().getLogger().debug("Stack: " + itemStack);
                }
            }

            BundleData bundleData;
            List<ItemStack> rawContents = itemStack.getComponent(DataComponentTypes.BUNDLE_CONTENTS);
            if (rawContents != null) {
                // Use existing list and transform it to support net IDs
                bundleData = new BundleData(session, rawContents);
            } else {
                // This is valid behavior (as of vanilla 1.21.2) if the bundle is empty.
                // Create new list
                bundleData = new BundleData();
            }
            itemStack.setBundleData(bundleData);
        }
    }

    public void markNewBundle(@Nullable BundleData bundleData) {
        if (bundleData == null) {
            return;
        }
        if (bundleData.bundleId != -1) {
            return;
        }
        bundleData.bundleId = nextBundleId++;
        for (GeyserItemStack stack : bundleData.contents()) {
            stack.setNetId(session.getNextItemNetId());
            session.getBundleCache().markNewBundle(stack.getBundleData());
        }
    }

    public ItemData checkForBundle(GeyserItemStack itemStack, ItemData.Builder itemData) {
        if (itemStack.getBundleData() == null) {
            return itemData.build();
        }
        // Not ideal, since Cloudburst NBT is immutable, but there isn't another ideal intersection between
        // item instance tracking and item translation
        // (Java just reads the contents of each item, while Bedrock kind of wants its own ID for each bundle item stack)
        List<GeyserItemStack> contents = itemStack.getBundleData().contents();
        int containerId = itemStack.getBundleId();

        if (containerId == -1) {
            session.getGeyser().getLogger().warning("Bundle ID should not be -1!");
        }

        NbtMap nbt = itemData.build().getTag();
        NbtMapBuilder builder = nbt == null ? NbtMap.builder() : nbt.toBuilder();
        builder.putInt("bundle_id", containerId);
        itemData.tag(builder.build());

        // Now that the tag is updated...
        ItemData finalItem = itemData.build();

        if (!itemStack.getBundleData().triggerFullContentsUpdate) {
            // We are probably in the middle of updating one slot. Let's save bandwidth! :)
            return finalItem;
        }

        // This is how BDS does it, so while it isn't pretty, it is accurate.
        // Ensure that all bundle slots are cleared when we re-send data.
        // Otherwise, if we don't indicate an item for a slot, Bedrock will think
        // the old item still exists.
        ItemData[] array = new ItemData[64];
        Arrays.fill(array, ItemData.AIR);
        List<ItemData> bedrockItems = Arrays.asList(array);
        // Reverse order to ensure contents line up with Java.
        int j = 0;
        for (int i = contents.size() - 1; i >= 0; i--) {
            // Ensure item data can be tracked
            bedrockItems.set(j++, contents.get(i).getItemData(session));
        }
        InventoryContentPacket packet = new InventoryContentPacket();
        packet.setContainerId(BUNDLE_CONTAINER_ID);
        packet.setContents(bedrockItems);
        packet.setContainerNameData(BundleCache.createContainer(containerId));
        packet.setStorageItem(finalItem);
        session.sendUpstreamPacket(packet);

        return finalItem;
    }

    /*
     * We need to send an InventorySlotPacket to the Bedrock client so it updates its changes and doesn't desync.
     */

    public void onItemAdded(GeyserItemStack bundle) {
        BundleData data = bundle.getBundleData();
        data.freshFromServer = false;
        data.triggerFullContentsUpdate = false;

        List<GeyserItemStack> contents = data.contents();
        int bedrockSlot = platformConvertSlot(contents.size(), 0);
        ItemData bedrockContent = contents.get(0).getItemData(session);

        sendInventoryPacket(data.bundleId(), bedrockSlot, bedrockContent, bundle.getItemData(session));

        data.triggerFullContentsUpdate = true;
    }

    public void onItemRemoved(GeyserItemStack bundle, int slot) {
        // Whatever item used to be in here should have been removed *before* this was triggered.
        BundleData data = bundle.getBundleData();
        data.freshFromServer = false;
        data.triggerFullContentsUpdate = false;

        List<GeyserItemStack> contents = data.contents();
        ItemData baseBundle = bundle.getItemData(session);
        // This first slot is now blank!
        sendInventoryPacket(data.bundleId(), platformConvertSlot(contents.size() + 1, 0), ItemData.AIR, baseBundle);
        // Adjust the index of every item that came before this item.
        for (int i = 0; i < slot; i++) {
            sendInventoryPacket(data.bundleId(), platformConvertSlot(contents.size(), i),
                contents.get(i).getItemData(session), baseBundle);
        }

        data.triggerFullContentsUpdate = true;
    }

    private void sendInventoryPacket(int bundleId, int bedrockSlot, ItemData bedrockContent, ItemData baseBundle) {
        InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(BUNDLE_CONTAINER_ID);
        packet.setItem(bedrockContent);
        packet.setSlot(bedrockSlot);
        packet.setContainerNameData(createContainer(bundleId));
        packet.setStorageItem(baseBundle);
        session.sendUpstreamPacket(packet);
    }

    /**
     * If a bundle is no longer present in the working inventory, delete the cache
     * from the client.
     */
    public void onOldItemDelete(GeyserItemStack itemStack) {
        if (itemStack.getBundleId() != -1) {
            // Clean up old container ID, to match BDS behavior.
            ContainerRegistryCleanupPacket packet = new ContainerRegistryCleanupPacket();
            packet.getContainers().add(createContainer(itemStack.getBundleId()));
            session.sendUpstreamPacket(packet);
        }
    }

    public void onInventoryClose(Inventory inventory) {
        if (inventory instanceof PlayerInventory) {
            // Don't bother; items are still here.
            return;
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            GeyserItemStack item = inventory.getItem(i);
            onOldItemDelete(item);
        }
    }

    /* All utilities to track when a release item packet should be sent.
    * As of 1.21.50, Bedrock seems to be picky and inspecific when sending its own release packet,
    * but if Java does not receive a release packet, then it will continue to drop items out of a bundle.
    * This workaround releases items on behalf of the client if it does not send a packet, while respecting
    * if Bedrock sends its own. */

    public void awaitRelease() {
        if (session.getTagCache().is(ItemTag.BUNDLES, session.getPlayerInventory().getItemInHand())) {
            releaseTick = session.getTicks() + 1;
        }
    }

    public void markRelease() {
        releaseTick = -1;
    }

    public void tick() {
        if (this.releaseTick != -1) {
            if (session.getTicks() >= this.releaseTick) {
                session.releaseItem();
                markRelease();
            }
        }
    }

    /**
     * Bidirectional; works for both Bedrock and Java.
     */
    public static int platformConvertSlot(int contentsSize, int rawSlot) {
        return contentsSize - rawSlot - 1;
    }

    public static FullContainerName createContainer(int id) {
        return new FullContainerName(ContainerSlotType.DYNAMIC_CONTAINER, id);
    }

    /**
     * Primarily exists to support net IDs within bundles.
     * Important to prevent accidental item deletion in creative mode.
     */
    public static final class BundleData {
        private final List<GeyserItemStack> contents;
        /**
         * Will be set to a positive integer after checking for existing bundle data.
         */
        private int bundleId = -1;
        /**
         * If false, blocks a complete InventoryContentPacket being sent to the server.
         */
        private boolean triggerFullContentsUpdate = true;
        /**
         * Sets whether data is accurate from the server; if so, any old bundle contents
         * will be overwritten.
         * This will be set to false if we are the most recent change-makers.
         */
        private boolean freshFromServer = true;

        BundleData(GeyserSession session, List<ItemStack> contents) {
            this();
            for (ItemStack content : contents) {
                GeyserItemStack itemStack = GeyserItemStack.from(content);
                // Check recursively
                session.getBundleCache().initialize(itemStack);
                this.contents.add(itemStack);
            }
        }

        BundleData() {
            this.contents = new ArrayList<>();
        }

        public int bundleId() {
            return bundleId;
        }

        public List<GeyserItemStack> contents() {
            return contents;
        }

        public boolean freshFromServer() {
            return freshFromServer;
        }

        public List<ItemStack> toComponent() {
            List<ItemStack> component = new ArrayList<>(this.contents.size());
            for (GeyserItemStack content : this.contents) {
                component.add(content.getItemStack());
            }
            return component;
        }

        /**
         * Merge in changes from the server and re-use net IDs where possible.
         */
        public void updateNetIds(GeyserSession session, BundleData oldData) {
            List<GeyserItemStack> oldContents = oldData.contents();
            // Items can't exactly be rearranged in a bundle; they can only be removed at an index, or inserted.
            int oldIndex = 0;
            for (int newIndex = 0; newIndex < this.contents.size(); newIndex++) {
                GeyserItemStack itemStack = this.contents.get(newIndex);
                if (oldIndex >= oldContents.size()) {
                    // Assume new item if it goes out of bounds of our existing stack
                    if (this.freshFromServer) {
                        // Only update net IDs for new items if the data is fresh from server.
                        // Otherwise, we can update net IDs for something that already has
                        // net IDs allocated, which can cause desyncs.
                        Inventory.updateItemNetId(GeyserItemStack.EMPTY, itemStack, session);
                        session.getBundleCache().markNewBundle(itemStack.getBundleData());
                    }
                    continue;
                }

                GeyserItemStack oldItem = oldContents.get(oldIndex);
                // If it stacks with the old item at this index, then
                if (!InventoryUtils.canStack(oldItem, itemStack)) {
                    // New item?
                    boolean found = false;
                    if (oldIndex + 1 < oldContents.size()) {
                        oldItem = oldContents.get(oldIndex + 1);
                        if (InventoryUtils.canStack(oldItem, itemStack)) {
                            // Permanently increment and assume all contents shifted here
                            oldIndex++;
                            found = true;
                        }
                    }
                    if (!found && oldIndex - 1 >= 0) {
                        oldItem = oldContents.get(oldIndex - 1);
                        if (InventoryUtils.canStack(oldItem, itemStack)) {
                            // Permanently decrement and assume all contents shifted here
                            oldIndex--;
                            found = true;
                        }
                    }
                    if (!found) {
                        oldItem = GeyserItemStack.EMPTY;
                    }
                }

                if (oldItem != GeyserItemStack.EMPTY || this.freshFromServer) {
                    Inventory.updateItemNetId(oldItem, itemStack, session);
                }
                oldIndex++;
            }
            this.bundleId = oldData.bundleId();
        }

        public BundleData copy() {
            BundleData data = new BundleData();
            data.bundleId = this.bundleId;
            for (GeyserItemStack content : this.contents) {
                data.contents.add(content.copy());
            }
            data.freshFromServer = this.freshFromServer;
            return data;
        }
    }
}
