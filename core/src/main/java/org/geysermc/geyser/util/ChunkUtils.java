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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntLists;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.chunk.BlockStorage;
import org.geysermc.geyser.level.chunk.GeyserChunkSection;
import org.geysermc.geyser.level.chunk.bitarray.SingletonBitArray;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;

@UtilityClass
public class ChunkUtils {

    public static final byte[] EMPTY_BIOME_DATA;

    public static final BlockStorage[] EMPTY_BLOCK_STORAGE;

    public static final int EMPTY_CHUNK_SECTION_SIZE;

    static {
        EMPTY_BLOCK_STORAGE = new BlockStorage[0];

        ByteBuf byteBuf = Unpooled.buffer();
        try {
            new GeyserChunkSection(EMPTY_BLOCK_STORAGE, 0)
                    .writeToNetwork(byteBuf);

            byte[] emptyChunkData = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(emptyChunkData);

            EMPTY_CHUNK_SECTION_SIZE = emptyChunkData.length;

            emptyChunkData = null;
        } finally {
            byteBuf.release();
        }

        byteBuf = Unpooled.buffer();
        try {
            BlockStorage blockStorage = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(0));
            blockStorage.writeToNetwork(byteBuf);

            EMPTY_BIOME_DATA = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(EMPTY_BIOME_DATA);
        } finally {
            byteBuf.release();
        }
    }

    public static int indexYZXtoXZY(int yzx) {
        return (yzx >> 8) | (yzx & 0x0F0) | ((yzx & 0x00F) << 8);
    }

    public static void updateChunkPosition(GeyserSession session, Vector3i position) {
        Vector2i chunkPos = session.getLastChunkPosition();
        Vector2i newChunkPos = Vector2i.from(position.getX() >> 4, position.getZ() >> 4);

        if (chunkPos == null || !chunkPos.equals(newChunkPos)) {
            NetworkChunkPublisherUpdatePacket chunkPublisherUpdatePacket = new NetworkChunkPublisherUpdatePacket();
            chunkPublisherUpdatePacket.setPosition(position);
            // Mitigates chunks not loading on 1.17.1 Paper and 1.19.3 Fabric. As of Bedrock 1.19.60.
            // https://github.com/GeyserMC/Geyser/issues/3490
            chunkPublisherUpdatePacket.setRadius(squareToCircle(session.getServerRenderDistance()) << 4);
            session.sendUpstreamPacket(chunkPublisherUpdatePacket);

            session.setLastChunkPosition(newChunkPos);
        }
    }

    /**
     * Converts a Java render distance number to the equivalent in Bedrock.
     */
    public static int squareToCircle(int renderDistance) {
        return GenericMath.ceil((renderDistance + 1) * MathUtils.SQRT_OF_TWO);
    }

    /**
     * Sends a block update to the Bedrock client. If the platform is not Spigot, this also
     * adds that block to the cache.
     * @param session the Bedrock session to send/register the block to
     * @param blockState the Java block state of the block
     * @param position the position of the block
     */
    public static void updateBlock(GeyserSession session, int blockState, Vector3i position) {
        updateBlockClientSide(session, BlockState.of(blockState), position);
        session.getChunkCache().updateBlock(position.getX(), position.getY(), position.getZ(), blockState);
    }

    /**
     * Sends a block update to the Bedrock client. If the platform does not have an integrated world manager, this also
     * adds that block to the cache.
     * @param session the Bedrock session to send/register the block to
     * @param blockState the Java block state of the block
     * @param position the position of the block
     */
    public static void updateBlock(GeyserSession session, BlockState blockState, Vector3i position) {
        updateBlockClientSide(session, blockState, position);
        session.getChunkCache().updateBlock(position.getX(), position.getY(), position.getZ(), blockState.javaId());
    }

    /**
     * Updates a block, but client-side only.
     */
    public static void updateBlockClientSide(GeyserSession session, BlockState blockState, Vector3i position) {
        // Checks for item frames so they aren't tripped up and removed
        ItemFrameEntity itemFrameEntity = ItemFrameEntity.getItemFrameEntity(session, position);
        if (itemFrameEntity != null) {
            if (blockState.is(Blocks.AIR)) { // Item frame is still present and no block overrides that; refresh it
                itemFrameEntity.updateBlock(true);
                // Still update the chunk cache with the new block if updateBlock is called
                return;
            }
            // Otherwise, let's still store our reference to the item frame, but let the new block take precedence for now
        }

        blockState.block().updateBlock(session, blockState, position);
    }

    public static void sendEmptyChunk(GeyserSession session, int chunkX, int chunkZ, boolean forceUpdate) {
        BedrockDimension bedrockDimension = session.getBedrockDimension();
        int bedrockSubChunkCount = bedrockDimension.height() >> 4;

        byte[] payload;
        // Allocate output buffer
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(ChunkUtils.EMPTY_BIOME_DATA.length * bedrockSubChunkCount + 1); // Consists only of biome data and border blocks
        try {
            byteBuf.writeBytes(EMPTY_BIOME_DATA);
            for (int i = 1; i < bedrockSubChunkCount; i++) {
                byteBuf.writeByte((127 << 1) | 1);
            }

            byteBuf.writeByte(0); // Border blocks - Edu edition only

            payload = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(payload);

            LevelChunkPacket data = new LevelChunkPacket();
            data.setDimension(session.getBedrockDimension().bedrockId());
            data.setChunkX(chunkX);
            data.setChunkZ(chunkZ);
            data.setSubChunksLength(0);
            data.setData(Unpooled.wrappedBuffer(payload));
            data.setCachingEnabled(false);
            session.sendUpstreamPacket(data);
        } finally {
            byteBuf.release();
        }

        if (forceUpdate) {
            Vector3i pos = Vector3i.from(chunkX << 4, 80, chunkZ << 4);
            UpdateBlockPacket blockPacket = new UpdateBlockPacket();
            blockPacket.setBlockPosition(pos);
            blockPacket.setDataLayer(0);
            blockPacket.setDefinition(session.getBlockMappings().getBedrockBlock(1));
            session.sendUpstreamPacket(blockPacket);
        }
    }

    public static void sendEmptyChunks(GeyserSession session, Vector3i position, int radius, boolean forceUpdate) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                sendEmptyChunk(session, chunkX + x, chunkZ + z, forceUpdate);
            }
        }
    }

    /**
     * Process the minimum and maximum heights for this dimension, and processes the world coordinate scale.
     * This must be done after the player has switched dimensions so we know what their dimension is
     */
    public static void loadDimension(GeyserSession session) {
        JavaDimension dimension = session.getDimensionType();
        int minY = dimension.minY();
        int maxY = dimension.maxY();

        BedrockDimension bedrockDimension = session.getBedrockDimension();
        // Yell in the console if the world height is too height in the current scenario
        // The constraints change depending on if the player is in the overworld or not, and if experimental height is enabled
        // (Ignore this for the Nether. We can't change that at the moment without the workaround. :/ )
        if (minY < bedrockDimension.minY() || (bedrockDimension.doUpperHeightWarn() && maxY > bedrockDimension.height())) {
            GeyserLogger.get().warning(GeyserLocale.getLocaleStringLog("geyser.network.translator.chunk.out_of_bounds",
                    String.valueOf(bedrockDimension.minY()),
                    String.valueOf(bedrockDimension.height()),
                    session.getRegistryCache().dimensions().byValue(session.getDimensionType())));
        }

        session.getChunkCache().setMinY(minY);
        session.getChunkCache().setHeightY(maxY);

        session.getWorldBorder().setWorldCoordinateScale(dimension.worldCoordinateScale());
    }
}
