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

package org.geysermc.geyser.translator.protocol.java.level;

import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.geysermc.erosion.util.BlockPositionIterator;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.translator.level.block.entity.BedrockOnlyBlockEntity;
import org.geysermc.geyser.util.BlockEntityUtils;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

import java.util.BitSet;

import static org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry.MessageType;
import static org.geysermc.geyser.level.block.BlockStateValues.JAVA_AIR_ID;

@Translator(packet = ClientboundSectionBlocksUpdatePacket.class)
public class JavaSectionBlocksUpdateTranslator extends PacketTranslator<ClientboundSectionBlocksUpdatePacket> {
    private static final int NEIGHBORS_NETWORK_FLAG = (1 << UpdateBlockPacket.Flag.NEIGHBORS.ordinal()) | (1 << UpdateBlockPacket.Flag.NETWORK.ordinal());
    private static final int NETWORK_FLAG = (1 << UpdateBlockPacket.Flag.NETWORK.ordinal());

    @Override
    public void translate(GeyserSession session, ClientboundSectionBlocksUpdatePacket packet) {
        // Send normal block updates if not many changes
        if (packet.getEntries().length < 32) {
            for (BlockChangeEntry entry : packet.getEntries()) {
                session.getWorldCache().updateServerCorrectBlockState(entry.getPosition(), entry.getBlock());
            }
            return;
        }

        UpdateSubChunkBlocksPacket subChunkBlocksPacket = new UpdateSubChunkBlocksPacket();
        subChunkBlocksPacket.setChunkX(packet.getChunkX());
        subChunkBlocksPacket.setChunkY(packet.getChunkY());
        subChunkBlocksPacket.setChunkZ(packet.getChunkZ());

        // If the entire section is updated, this might be a legacy non-full chunk update
        // which can contain thousands of unchanged blocks
        if (packet.getEntries().length == 4096 && !session.getGeyser().getWorldManager().hasOwnChunkCache()) {
            // hack - bedrock might ignore the block updates if the chunk was still loading.
            // sending an UpdateBlockPacket seems to force it
            BlockChangeEntry firstEntry = packet.getEntries()[0];
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setBlockPosition(firstEntry.getPosition());
            blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(firstEntry.getBlock()));
            blockPacket.setDataLayer(0);
            session.sendUpstreamPacket(blockPacket);

            // Filter out unchanged blocks
            Vector3i offset = Vector3i.from(packet.getChunkX() << 4, packet.getChunkY() << 4, packet.getChunkZ() << 4);
            BlockPositionIterator blockIter = BlockPositionIterator.fromMinMax(
                    offset.getX(), offset.getY(), offset.getZ(),
                    offset.getX() + 15, offset.getY() + 15, offset.getZ() + 15
            );

            int[] sectionBlocks = session.getGeyser().getWorldManager().getBlocksAt(session, blockIter);
            BitSet waterlogged = BlockRegistries.WATERLOGGED.get();
            for (BlockChangeEntry entry : packet.getEntries()) {
                Vector3i pos = entry.getPosition().sub(offset);
                int index = pos.getZ() + pos.getX() * 16 + pos.getY() * 256;
                int oldBlockState = sectionBlocks[index];
                if (oldBlockState != entry.getBlock()) {
                    // Avoid sending unnecessary waterlogged updates
                    boolean updateWaterlogged = waterlogged.get(oldBlockState) != waterlogged.get(entry.getBlock());
                    applyEntry(session, entry, subChunkBlocksPacket, updateWaterlogged);
                }
            }
        } else {
            for (BlockChangeEntry entry : packet.getEntries()) {
                applyEntry(session, entry, subChunkBlocksPacket, true);
            }
        }

        session.sendUpstreamPacket(subChunkBlocksPacket);

        // Post block update
        for (BlockChangeEntry entry : packet.getEntries()) {
            session.getWorldCache().removePrediction(entry.getPosition());
            BlockStateValues.getLecternBookStates().handleBlockChange(session, entry.getBlock(), entry.getPosition());

            // Iterates through all Bedrock-only block entity translators and determines if a manual block entity packet
            // needs to be sent
            for (BedrockOnlyBlockEntity bedrockOnlyBlockEntity : BlockEntityUtils.BEDROCK_ONLY_BLOCK_ENTITIES) {
                if (bedrockOnlyBlockEntity.isBlock(entry.getBlock())) {
                    // Flower pots are block entities only in Bedrock and are not updated anywhere else like note blocks
                    bedrockOnlyBlockEntity.updateBlock(session, entry.getBlock(), entry.getPosition());
                    break; //No block will be a part of two classes
                }
            }
        }
    }

    // Modified version of ChunkUtils#updateBlockClientSide
    private static void applyEntry(GeyserSession session, BlockChangeEntry entry, UpdateSubChunkBlocksPacket subChunkBlocksPacket, boolean updateWaterlogged) {
        Vector3i position = entry.getPosition();
        int blockState = entry.getBlock();

        session.getChunkCache().updateBlock(position.getX(), position.getY(), position.getZ(), blockState);

        // Checks for item frames so they aren't tripped up and removed
        ItemFrameEntity itemFrameEntity = ItemFrameEntity.getItemFrameEntity(session, position);
        if (itemFrameEntity != null) {
            if (blockState == JAVA_AIR_ID) { // Item frame is still present and no block overrides that; refresh it
                itemFrameEntity.updateBlock(true);
                // Still update the chunk cache with the new block if updateBlock is called
                return;
            }
            // Otherwise, let's still store our reference to the item frame, but let the new block take precedence for now
        }

        BlockDefinition definition = session.getBlockMappings().getBedrockBlock(blockState);

        int skullVariant = BlockStateValues.getSkullVariant(blockState);
        if (skullVariant == -1) {
            // Skull is gone
            session.getSkullCache().removeSkull(position);
        } else if (skullVariant == 3) {
            // The changed block was a player skull so check if a custom block was defined for this skull
            SkullCache.Skull skull = session.getSkullCache().updateSkull(position, blockState);
            if (skull != null && skull.getBlockDefinition() != null) {
                definition = skull.getBlockDefinition();
            }
        }

        // Prevent moving_piston from being placed
        // It's used for extending piston heads, but it isn't needed on Bedrock and causes pistons to flicker
        if (!BlockStateValues.isMovingPiston(blockState)) {
            subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                    position,
                    definition,
                    NEIGHBORS_NETWORK_FLAG,
                    -1,
                    MessageType.NONE
            ));

            if (updateWaterlogged) {
                BlockDefinition waterDefinition = BlockRegistries.WATERLOGGED.get().get(blockState) ?
                        session.getBlockMappings().getBedrockWater() : session.getBlockMappings().getBedrockAir();
                subChunkBlocksPacket.getExtraBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                        position,
                        waterDefinition,
                        0,
                        -1,
                        MessageType.NONE
                ));
            }
        }

        // Extended collision boxes for custom blocks
        if (!session.getBlockMappings().getExtendedCollisionBoxes().isEmpty()) {
            int aboveBlock = session.getGeyser().getWorldManager().getBlockAt(session, position.getX(), position.getY() + 1, position.getZ());
            BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(blockState);
            int belowBlock = session.getGeyser().getWorldManager().getBlockAt(session, position.getX(), position.getY() - 1, position.getZ());
            BlockDefinition belowBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(belowBlock);
            if (belowBedrockExtendedCollisionDefinition != null && blockState == BlockStateValues.JAVA_AIR_ID) {
                subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                        position,
                        belowBedrockExtendedCollisionDefinition,
                        NETWORK_FLAG,
                        -1,
                        MessageType.NONE
                ));
            } else if (aboveBedrockExtendedCollisionDefinition != null && aboveBlock == BlockStateValues.JAVA_AIR_ID) {
                subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                        position.add(0, 1, 0),
                        aboveBedrockExtendedCollisionDefinition,
                        NETWORK_FLAG,
                        -1,
                        MessageType.NONE
                ));
            } else if (aboveBlock == BlockStateValues.JAVA_AIR_ID) {
                subChunkBlocksPacket.getStandardBlocks().add(new org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry(
                        position.add(0, 1, 0),
                        session.getBlockMappings().getBedrockAir(),
                        NETWORK_FLAG,
                        -1,
                        MessageType.NONE
                ));
            }
        }
    }
}
