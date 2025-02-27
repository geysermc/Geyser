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

package org.geysermc.geyser.translator.inventory.chest;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.Container;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.ChestType;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.block.entity.DoubleChestBlockEntityTranslator;
import org.geysermc.geyser.util.InventoryUtils;

public class DoubleChestInventoryTranslator extends ChestInventoryTranslator {
    private final int defaultJavaBlockState;

    public DoubleChestInventoryTranslator(int size) {
        super(size, 54);
        this.defaultJavaBlockState = Blocks.CHEST.defaultBlockState()
                .withValue(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .withValue(Properties.CHEST_TYPE, ChestType.SINGLE)
                .javaId();
    }

    @Override
    public boolean prepareInventory(GeyserSession session, Inventory inventory) {
        // See BlockInventoryHolder - same concept there except we're also dealing with a specific block state
        if (session.getLastInteractionPlayerPosition().equals(session.getPlayerEntity().getPosition())) {
            BlockState state = session.getGeyser().getWorldManager().blockAt(session, session.getLastInteractionBlockPosition());
            if (!BlockRegistries.CUSTOM_BLOCK_STATE_OVERRIDES.get().containsKey(state.javaId())) {
                if ((state.block() == Blocks.CHEST || state.block() == Blocks.TRAPPED_CHEST)
                        && state.getValue(Properties.CHEST_TYPE) != ChestType.SINGLE) {
                    inventory.setHolderPosition(session.getLastInteractionBlockPosition());
                    ((Container) inventory).setUsingRealBlock(true, state.block());

                    NbtMapBuilder tag = BlockEntityTranslator.getConstantBedrockTag("Chest", session.getLastInteractionBlockPosition())
                            .putString("CustomName", inventory.getTitle());

                    DoubleChestBlockEntityTranslator.translateChestValue(tag, state,
                            session.getLastInteractionBlockPosition().getX(), session.getLastInteractionBlockPosition().getZ());

                    BlockEntityDataPacket dataPacket = new BlockEntityDataPacket();
                    dataPacket.setData(tag.build());
                    dataPacket.setBlockPosition(session.getLastInteractionBlockPosition());
                    session.sendUpstreamPacket(dataPacket);

                    return true;
                }
            }
        }

        Vector3i position = InventoryUtils.findAvailableWorldSpace(session);
        if (position == null) {
            return false;
        }

        Vector3i pairPosition = position.add(Vector3i.UNIT_X);
        BlockDefinition definition = session.getBlockMappings().getVanillaBedrockBlock(defaultJavaBlockState);

        UpdateBlockPacket blockPacket = new UpdateBlockPacket();
        blockPacket.setDataLayer(0);
        blockPacket.setBlockPosition(position);
        blockPacket.setDefinition(definition);
        blockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        session.sendUpstreamPacket(blockPacket);

        NbtMap tag = BlockEntityTranslator.getConstantBedrockTag("Chest", position)
                .putInt("pairx", pairPosition.getX())
                .putInt("pairz", pairPosition.getZ())
                .putString("CustomName", inventory.getTitle()).build();
        BlockEntityDataPacket dataPacket = new BlockEntityDataPacket();
        dataPacket.setData(tag);
        dataPacket.setBlockPosition(position);
        session.sendUpstreamPacket(dataPacket);

        blockPacket = new UpdateBlockPacket();
        blockPacket.setDataLayer(0);
        blockPacket.setBlockPosition(pairPosition);
        blockPacket.setDefinition(definition);
        blockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        session.sendUpstreamPacket(blockPacket);

        tag = NbtMap.builder()
                .putString("id", "Chest")
                .putInt("x", pairPosition.getX())
                .putInt("y", pairPosition.getY())
                .putInt("z", pairPosition.getZ())
                .putInt("pairx", position.getX())
                .putInt("pairz", position.getZ())
                .putString("CustomName", inventory.getTitle()).build();
        dataPacket = new BlockEntityDataPacket();
        dataPacket.setData(tag);
        dataPacket.setBlockPosition(pairPosition);
        session.sendUpstreamPacket(dataPacket);

        inventory.setHolderPosition(position);

        return true;
    }

    @Override
    public void openInventory(GeyserSession session, Inventory inventory) {
        ContainerOpenPacket containerOpenPacket = new ContainerOpenPacket();
        containerOpenPacket.setId((byte) inventory.getBedrockId());
        containerOpenPacket.setType(ContainerType.CONTAINER);
        containerOpenPacket.setBlockPosition(inventory.getHolderPosition());
        containerOpenPacket.setUniqueEntityId(inventory.getHolderId());
        session.sendUpstreamPacket(containerOpenPacket);
    }

    @Override
    public void closeInventory(GeyserSession session, Inventory inventory) {
        if (!(inventory instanceof Container container)) {
            GeyserLogger.get().warning("Tried to close a non-container inventory in a block inventory holder! Please report this error on discord.");
            GeyserLogger.get().warning("Current inventory translator: " + session.getInventoryTranslator().getClass().getSimpleName());
            GeyserLogger.get().warning("Current inventory: " + inventory.getClass().getSimpleName());
            // Try to save ourselves? maybe?
            // https://github.com/GeyserMC/Geyser/issues/4141
            // TODO: improve once this issue is pinned down
            session.setOpenInventory(null);
            session.setInventoryTranslator(InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR);
            return;
        }

        if (container.isUsingRealBlock()) {
            // No need to reset a block since we didn't change any blocks
            // But send a container close packet because we aren't destroying the original.
            ContainerClosePacket packet = new ContainerClosePacket();
            packet.setId((byte) inventory.getBedrockId());
            packet.setServerInitiated(true);
            packet.setType(ContainerType.CONTAINER);
            session.sendUpstreamPacket(packet);
            return;
        }

        Vector3i holderPos = inventory.getHolderPosition();
        int realBlock = session.getGeyser().getWorldManager().getBlockAt(session, holderPos);
        UpdateBlockPacket blockPacket = new UpdateBlockPacket();
        blockPacket.setDataLayer(0);
        blockPacket.setBlockPosition(holderPos);
        blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(realBlock));
        session.sendUpstreamPacket(blockPacket);

        holderPos = holderPos.add(Vector3i.UNIT_X);
        realBlock = session.getGeyser().getWorldManager().getBlockAt(session, holderPos);
        blockPacket = new UpdateBlockPacket();
        blockPacket.setDataLayer(0);
        blockPacket.setBlockPosition(holderPos);
        blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(realBlock));
        session.sendUpstreamPacket(blockPacket);
    }
}
