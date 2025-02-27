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

package org.geysermc.geyser.inventory.holder;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.Container;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.LecternContainer;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.util.InventoryUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the fake block we implement for each inventory, should we need to.
 * This class will attempt to use a real block first, if possible.
 */
public class BlockInventoryHolder extends InventoryHolder {
    /**
     * The default Java block ID to translate as a fake block
     */
    private final BlockState defaultJavaBlockState;
    private final ContainerType containerType;
    private final Set<Block> validBlocks;

    public BlockInventoryHolder(Block defaultJavaBlock, ContainerType containerType, Block... validBlocks) {
        this(defaultJavaBlock.defaultBlockState(), containerType, validBlocks);
    }

    public BlockInventoryHolder(BlockState defaultJavaBlockState, ContainerType containerType, Block... validBlocks) {
        this.defaultJavaBlockState = defaultJavaBlockState;
        this.containerType = containerType;
        if (validBlocks != null) {
            Set<Block> validBlocksTemp = new HashSet<>(validBlocks.length + 1);
            Collections.addAll(validBlocksTemp, validBlocks);
            validBlocksTemp.add(defaultJavaBlockState.block());
            this.validBlocks = Set.copyOf(validBlocksTemp);
        } else {
            this.validBlocks = Collections.singleton(defaultJavaBlockState.block());
        }
    }

    @Override
    public boolean prepareInventory(InventoryTranslator translator, GeyserSession session, Inventory inventory) {
        // Check to see if there is an existing block we can use that the player just selected.
        // First, verify that the player's position has not changed, so we don't try to select a block wildly out of range.
        // (This could be a virtual inventory that the player is opening)
        if (checkInteractionPosition(session)) {
            // Then, check to see if the interacted block is valid for this inventory by ensuring the block state identifier is valid
            // and the bedrock block is vanilla
            BlockState state = session.getGeyser().getWorldManager().blockAt(session, session.getLastInteractionBlockPosition());
            if (!BlockRegistries.CUSTOM_BLOCK_STATE_OVERRIDES.get().containsKey(state.javaId())) {
                if (isValidBlock(state)) {
                    // We can safely use this block
                    inventory.setHolderPosition(session.getLastInteractionBlockPosition());
                    ((Container) inventory).setUsingRealBlock(true, state.block());
                    setCustomName(session, session.getLastInteractionBlockPosition(), inventory, state);

                    return true;
                }
            }
        }

        Vector3i position = InventoryUtils.findAvailableWorldSpace(session);
        if (position == null) {
            return false;
        }

        UpdateBlockPacket blockPacket = new UpdateBlockPacket();
        blockPacket.setDataLayer(0);
        blockPacket.setBlockPosition(position);
        blockPacket.setDefinition(session.getBlockMappings().getVanillaBedrockBlock(defaultJavaBlockState));
        blockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        session.sendUpstreamPacket(blockPacket);
        inventory.setHolderPosition(position);

        setCustomName(session, position, inventory, defaultJavaBlockState);

        return true;
    }

    /**
     * Will be overwritten in the beacon inventory translator to remove the check, since virtual inventories can't exist.
     *
     * @return if the player's last interaction position and current position match. Used to ensure that we don't select
     * a block to hold the inventory that's wildly out of range.
     */
    protected boolean checkInteractionPosition(GeyserSession session) {
        return session.getLastInteractionPlayerPosition().equals(session.getPlayerEntity().getPosition());
    }

    /**
     * @return true if this Java block ID can be used for player inventory.
     */
    protected boolean isValidBlock(BlockState blockState) {
        return this.validBlocks.contains(blockState.block());
    }

    protected void setCustomName(GeyserSession session, Vector3i position, Inventory inventory, BlockState javaBlockState) {
        NbtMap tag = NbtMap.builder()
                .putInt("x", position.getX())
                .putInt("y", position.getY())
                .putInt("z", position.getZ())
                .putString("CustomName", inventory.getTitle()).build();
        BlockEntityDataPacket dataPacket = new BlockEntityDataPacket();
        dataPacket.setData(tag);
        dataPacket.setBlockPosition(position);
        session.sendUpstreamPacket(dataPacket);
    }

    @Override
    public void openInventory(InventoryTranslator translator, GeyserSession session, Inventory inventory) {
        ContainerOpenPacket containerOpenPacket = new ContainerOpenPacket();
        containerOpenPacket.setId((byte) inventory.getBedrockId());
        containerOpenPacket.setType(containerType);
        containerOpenPacket.setBlockPosition(inventory.getHolderPosition());
        containerOpenPacket.setUniqueEntityId(inventory.getHolderId());
        session.sendUpstreamPacket(containerOpenPacket);
    }

    @Override
    public void closeInventory(InventoryTranslator translator, GeyserSession session, Inventory inventory, ContainerType type) {
        if (!(inventory instanceof Container container)) {
            GeyserLogger.get().warning("Tried to close a non-container inventory in a block inventory holder! Please report this error on discord.");
            GeyserLogger.get().warning("Current inventory translator: " + translator.getClass().getSimpleName());
            GeyserLogger.get().warning("Current inventory: " + inventory.getClass().getSimpleName());
            // Try to save ourselves? maybe?
            // https://github.com/GeyserMC/Geyser/issues/4141
            // TODO: improve once this issue is pinned down
            session.setOpenInventory(null);
            session.setInventoryTranslator(InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR);
            return;
        }

        // Bedrock broke inventory closing. I wish i was kidding.
        // "type" is explicitly passed to keep track of which inventory types can be closed without
        // ""workarounds"". yippie.
        // Further, Lecterns cannot be closed with any of the two methods below.
        if (container.isUsingRealBlock() && !(container instanceof LecternContainer)) {
            if (type != null) {
                // No need to reset a block since we didn't change any blocks
                // But send a container close packet because we aren't destroying the original.
                ContainerClosePacket packet = new ContainerClosePacket();
                packet.setId((byte) inventory.getBedrockId());
                packet.setServerInitiated(true);
                packet.setType(type);
                session.sendUpstreamPacket(packet);
                return;
            }

            // Destroy the block. There's no inventory to view => it gets closed!
            Vector3i holderPos = inventory.getHolderPosition();
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setDataLayer(0);
            blockPacket.setBlockPosition(holderPos);
            blockPacket.setDefinition(session.getBlockMappings().getBedrockAir());
            blockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
            session.sendUpstreamPacket(blockPacket);
        }

        // Reset to correct block
        Vector3i holderPos = inventory.getHolderPosition();
        int realBlock = session.getGeyser().getWorldManager().getBlockAt(session, holderPos.getX(), holderPos.getY(), holderPos.getZ());
        UpdateBlockPacket blockPacket = new UpdateBlockPacket();
        blockPacket.setDataLayer(0);
        blockPacket.setBlockPosition(holderPos);
        blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(realBlock));
        blockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        session.sendUpstreamPacket(blockPacket);
    }
}
