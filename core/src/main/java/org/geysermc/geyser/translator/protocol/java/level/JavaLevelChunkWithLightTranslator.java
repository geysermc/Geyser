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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.chunk.BlockStorage;
import org.geysermc.geyser.level.chunk.GeyserChunkSection;
import org.geysermc.geyser.level.chunk.bitarray.BitArray;
import org.geysermc.geyser.level.chunk.bitarray.BitArrayVersion;
import org.geysermc.geyser.level.chunk.bitarray.SingletonBitArray;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.level.BiomeTranslator;
import org.geysermc.geyser.translator.level.block.entity.BedrockChunkWantsBlockEntityTag;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.block.entity.SkullBlockEntityTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.BlockEntityUtils;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.BitStorage;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.GlobalPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.SingletonPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.geysermc.geyser.util.ChunkUtils.*;

@Translator(packet = ClientboundLevelChunkWithLightPacket.class)
public class JavaLevelChunkWithLightTranslator extends PacketTranslator<ClientboundLevelChunkWithLightPacket> {
    private static final ThreadLocal<ExtendedCollisionsStorage> EXTENDED_COLLISIONS_STORAGE = ThreadLocal.withInitial(ExtendedCollisionsStorage::new);

    @Override
    public void translate(GeyserSession session, ClientboundLevelChunkWithLightPacket packet) {
        final boolean useExtendedCollisions = !session.getBlockMappings().getExtendedCollisionBoxes().isEmpty();

        if (session.isSpawned()) {
            ChunkUtils.updateChunkPosition(session, session.getPlayerEntity().getPosition().toInt());
        }

        // Ensure that, if the player is using lower world heights, the position is not offset
        int yOffset = session.getChunkCache().getChunkMinY();
        int chunkSize = session.getChunkCache().getChunkHeightY();

        DataPalette[] javaChunks = new DataPalette[chunkSize];
        DataPalette[] javaBiomes = new DataPalette[chunkSize];

        final BlockEntityInfo[] blockEntities = packet.getBlockEntities();
        final List<NbtMap> bedrockBlockEntities = new ObjectArrayList<>(blockEntities.length);

        BitSet waterloggedPaletteIds = new BitSet();
        BitSet bedrockOnlyBlockEntityIds = new BitSet();

        BedrockDimension bedrockDimension = session.getBedrockDimension();
        int maxBedrockSectionY = (bedrockDimension.height() >> 4) - 1;

        int sectionCount;
        byte[] payload;
        ByteBuf byteBuf = null;
        GeyserChunkSection[] sections = new GeyserChunkSection[javaChunks.length - (yOffset + (bedrockDimension.minY() >> 4))];

        try {
            ByteBuf in = Unpooled.wrappedBuffer(packet.getChunkData());
            boolean extendedCollisionNextSection = false;
            for (int sectionY = 0; sectionY < chunkSize; sectionY++) {
                ChunkSection javaSection = MinecraftTypes.readChunkSection(in);
                javaChunks[sectionY] = javaSection.getChunkData();
                javaBiomes[sectionY] = javaSection.getBiomeData();
                boolean extendedCollision = extendedCollisionNextSection;
                boolean thisExtendedCollisionNextSection = false;

                int bedrockSectionY = sectionY + (yOffset - (bedrockDimension.minY() >> 4));
                int subChunkIndex = sectionY + yOffset;
                if (bedrockSectionY < 0 || maxBedrockSectionY < bedrockSectionY) {
                    // Ignore this chunk section since it goes outside the bounds accepted by the Bedrock client
                    if (useExtendedCollisions) {
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                    }
                    extendedCollisionNextSection = false;
                    continue;
                }

                // No need to encode an empty section...
                if (javaSection.isBlockCountEmpty()) {
                    // Unless we need to send extended collisions
                    if (useExtendedCollisions) {
                        if (extendedCollision) {
                            int blocks = EXTENDED_COLLISIONS_STORAGE.get().bottomLayerCollisions() + 1;
                            BitArray bedrockData = BitArrayVersion.forBitsCeil(Integer.SIZE - Integer.numberOfLeadingZeros(blocks)).createArray(BlockStorage.SIZE);
                            BlockStorage layer0 = new BlockStorage(bedrockData, new IntArrayList(blocks));
    
                            layer0.idFor(session.getBlockMappings().getBedrockAir().getRuntimeId());
                            for (int yzx = 0; yzx < BlockStorage.SIZE / 16; yzx++) {
                                if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                                    bedrockData.set(indexYZXtoXZY(yzx), layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                                    EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                                }
                            }
    
                            BlockStorage[] layers = new BlockStorage[]{ layer0 };
                            sections[bedrockSectionY] = new GeyserChunkSection(layers, subChunkIndex);
                        }
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                        extendedCollisionNextSection = false;
                    }
                    continue;
                }

                Palette javaPalette = javaSection.getChunkData().getPalette();
                BitStorage javaData = javaSection.getChunkData().getStorage();

                if (javaPalette instanceof GlobalPalette) {
                    // As this is the global palette, simply iterate through the whole chunk section once
                    GeyserChunkSection section = new GeyserChunkSection(session.getBlockMappings().getBedrockAir().getRuntimeId(), subChunkIndex);
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int javaId = javaData.get(yzx);
                        BlockState state = BlockState.of(javaId);
                        int bedrockId = session.getBlockMappings().getBedrockBlockId(javaId);
                        int xzy = indexYZXtoXZY(yzx);
                        section.getBlockStorageArray()[0].setFullBlock(xzy, bedrockId);

                        if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                            section.getBlockStorageArray()[1].setFullBlock(xzy, session.getBlockMappings().getBedrockWater().getRuntimeId());
                        }

                        // Extended collision blocks
                        if (useExtendedCollisions) {
                            if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                                if (javaId == Block.JAVA_AIR_ID) {
                                    section.getBlockStorageArray()[0].setFullBlock(xzy, EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY));
                                }
                                EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                                continue;
                            }
                            BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(javaId);
                            if (aboveBedrockExtendedCollisionDefinition != null) {
                                EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                                if ((xzy & 0xF) == 15) {
                                    thisExtendedCollisionNextSection = true;
                                }
                            }
                        }

                        // Check if block is piston or flower to see if we'll need to create additional block entities, as they're only block entities in Bedrock
                        if (state.block() instanceof BedrockChunkWantsBlockEntityTag blockEntity) {
                            bedrockBlockEntities.add(blockEntity.createTag(session,
                                    Vector3i.from((packet.getX() << 4) + (yzx & 0xF), ((sectionY + yOffset) << 4) + ((yzx >> 8) & 0xF), (packet.getZ() << 4) + ((yzx >> 4) & 0xF)),
                                    state
                            ));
                        }
                    }
                    sections[bedrockSectionY] = section;
                    extendedCollisionNextSection = thisExtendedCollisionNextSection;
                    continue;
                }

                if (javaPalette instanceof SingletonPalette) {
                    // There's only one block here. Very easy!
                    int javaId = javaPalette.idToState(0);
                    int bedrockId = session.getBlockMappings().getBedrockBlockId(javaId);
                    BlockStorage blockStorage = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(bedrockId));

                    if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                        BlockStorage waterlogged = new BlockStorage(SingletonBitArray.INSTANCE, IntLists.singleton(session.getBlockMappings().getBedrockWater().getRuntimeId()));
                        sections[bedrockSectionY] = new GeyserChunkSection(new BlockStorage[] {blockStorage, waterlogged}, subChunkIndex);
                    } else {
                        sections[bedrockSectionY] = new GeyserChunkSection(new BlockStorage[] {blockStorage}, subChunkIndex);
                    }
                    if (useExtendedCollisions) {
                        EXTENDED_COLLISIONS_STORAGE.get().clear();
                        extendedCollisionNextSection = false;
                    }
                    // If a chunk contains all of the same piston or flower pot then god help us
                    continue;
                }

                IntList bedrockPalette = new IntArrayList(javaPalette.size());
                int airPaletteId = -1;
                waterloggedPaletteIds.clear();
                bedrockOnlyBlockEntityIds.clear();

                // Iterate through palette and convert state IDs to Bedrock, doing some additional checks as we go
                int extendedCollisionsInPalette = 0;
                for (int i = 0; i < javaPalette.size(); i++) {
                    int javaId = javaPalette.idToState(i);
                    bedrockPalette.add(session.getBlockMappings().getBedrockBlockId(javaId));

                    if (BlockRegistries.WATERLOGGED.get().get(javaId)) {
                        waterloggedPaletteIds.set(i);
                    }

                    if (javaId == Block.JAVA_AIR_ID) {
                        airPaletteId = i;
                    }

                    if (useExtendedCollisions) {
                        if (session.getBlockMappings().getExtendedCollisionBoxes().get(javaId) != null) {
                            extendedCollision = true;
                            extendedCollisionsInPalette++;
                        }
                    }

                    // Check if block is piston, flower or cauldron to see if we'll need to create additional block entities, as they're only block entities in Bedrock
                    // TODO this needs a performance check when my head is clearer
                    BlockState state = BlockState.of(javaId);
                    if (state.block() instanceof BedrockChunkWantsBlockEntityTag) {
                        bedrockOnlyBlockEntityIds.set(i);
                    }
                }

                // Add Bedrock-exclusive block entities
                // We only if the palette contained any blocks that are Bedrock-exclusive block entities to avoid iterating through the whole block data
                // for no reason, as most sections will not contain any pistons or flower pots
                if (!bedrockOnlyBlockEntityIds.isEmpty()) {
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        if (bedrockOnlyBlockEntityIds.get(paletteId)) {
                            BlockState state = BlockState.of(javaPalette.idToState(paletteId));
                            bedrockBlockEntities.add(((BedrockChunkWantsBlockEntityTag) state.block()).createTag(session,
                                    Vector3i.from((packet.getX() << 4) + (yzx & 0xF), ((sectionY + yOffset) << 4) + ((yzx >> 8) & 0xF), (packet.getZ() << 4) + ((yzx >> 4) & 0xF)),
                                    state
                            ));
                        }
                    }
                }

                // We need to ensure we use enough bits to represent extended collision blocks in the chunk section
                int sectionCollisionBlocks = 0;
                if (useExtendedCollisions) {
                    int bottomLayerCollisions = extendedCollision ? EXTENDED_COLLISIONS_STORAGE.get().bottomLayerCollisions() : 0;
                    sectionCollisionBlocks = bottomLayerCollisions + extendedCollisionsInPalette;
                }
                int bedrockDataBits = Integer.SIZE - Integer.numberOfLeadingZeros(javaPalette.size() + sectionCollisionBlocks);
                BitArray bedrockData = BitArrayVersion.forBitsCeil(bedrockDataBits).createArray(BlockStorage.SIZE);
                BlockStorage layer0 = new BlockStorage(bedrockData, bedrockPalette);
                BlockStorage[] layers;

                // Convert data array from YZX to XZY coordinate order
                if (waterloggedPaletteIds.isEmpty() && !extendedCollision) {
                    // No blocks are waterlogged, simply convert coordinate order
                    // This could probably be optimized further...
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);
                    }

                    layers = new BlockStorage[]{ layer0 };
                } else if (!waterloggedPaletteIds.isEmpty() && !extendedCollision) {
                    // The section contains waterlogged blocks, we need to convert coordinate order AND generate a V1 block storage for
                    // layer 1 with palette ID 1 indicating water
                    int[] layer1Data = new int[BlockStorage.SIZE >> 5];
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);

                        if (waterloggedPaletteIds.get(paletteId)) {
                            layer1Data[xzy >> 5] |= 1 << (xzy & 0x1F);
                        }
                    }
                    
                    // V1 palette
                    IntList layer1Palette = IntList.of(
                            session.getBlockMappings().getBedrockAir().getRuntimeId(), // Air - see BlockStorage's constructor for more information
                            session.getBlockMappings().getBedrockWater().getRuntimeId());

                    layers = new BlockStorage[]{ layer0, new BlockStorage(BitArrayVersion.V1.createArray(BlockStorage.SIZE, layer1Data), layer1Palette) };
                } else if (waterloggedPaletteIds.isEmpty()) {
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);

                        if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                            if (paletteId == airPaletteId) {
                                bedrockData.set(xzy, layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                            }
                            EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                            continue;
                        }
                        BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings()
                                .getExtendedCollisionBoxes().get(javaPalette.idToState(paletteId));
                        if (aboveBedrockExtendedCollisionDefinition != null) {
                            EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                            if ((xzy & 0xF) == 15) {
                                thisExtendedCollisionNextSection = true;
                            }
                        }
                    }

                    layers = new BlockStorage[]{ layer0 };
                } else {
                    int[] layer1Data = new int[BlockStorage.SIZE >> 5];
                    for (int yzx = 0; yzx < BlockStorage.SIZE; yzx++) {
                        int paletteId = javaData.get(yzx);
                        int xzy = indexYZXtoXZY(yzx);
                        bedrockData.set(xzy, paletteId);

                        if (waterloggedPaletteIds.get(paletteId)) {
                            layer1Data[xzy >> 5] |= 1 << (xzy & 0x1F);
                        }

                        if (EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY) != 0) {
                            if (paletteId == airPaletteId) {
                                bedrockData.set(xzy, layer0.idFor(EXTENDED_COLLISIONS_STORAGE.get().get(yzx, sectionY)));
                            }
                            EXTENDED_COLLISIONS_STORAGE.get().set(yzx, 0, sectionY);
                            continue;
                        }
                        BlockDefinition aboveBedrockExtendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes()
                                .get(javaPalette.idToState(paletteId));
                        if (aboveBedrockExtendedCollisionDefinition != null) {
                            EXTENDED_COLLISIONS_STORAGE.get().set((yzx + 0x100) & 0xFFF, aboveBedrockExtendedCollisionDefinition.getRuntimeId(), sectionY);
                            if ((xzy & 0xF) == 15) {
                                thisExtendedCollisionNextSection = true;
                            }
                        }
                    }

                    // V1 palette
                    IntList layer1Palette = IntList.of(
                            session.getBlockMappings().getBedrockAir().getRuntimeId(), // Air - see BlockStorage's constructor for more information
                            session.getBlockMappings().getBedrockWater().getRuntimeId());

                    layers = new BlockStorage[]{ layer0, new BlockStorage(BitArrayVersion.V1.createArray(BlockStorage.SIZE, layer1Data), layer1Palette) };
                }

                sections[bedrockSectionY] = new GeyserChunkSection(layers, subChunkIndex);
                extendedCollisionNextSection = thisExtendedCollisionNextSection;
            }

            if (!session.getErosionHandler().isActive()) {
                session.getChunkCache().addToCache(packet.getX(), packet.getZ(), javaChunks);
            }

            final int chunkBlockX = packet.getX() << 4;
            final int chunkBlockZ = packet.getZ() << 4;
            for (BlockEntityInfo blockEntity : blockEntities) {
                BlockEntityType type = blockEntity.getType();
                NbtMap tag = blockEntity.getNbt();
                if (type == null) {
                    // As an example: ViaVersion will send -1 if it cannot find the block entity type
                    // Vanilla Minecraft gracefully handles this
                    continue;
                }
                int x = blockEntity.getX(); // Relative to chunk
                int y = blockEntity.getY();
                int z = blockEntity.getZ(); // Relative to chunk

                // Get the Java block state ID from block entity position
                DataPalette section = javaChunks[(y >> 4) - yOffset];
                BlockState blockState = BlockState.of(section.get(x, y & 0xF, z));

                // Note that, since 1.20.5, tags can be null, but Bedrock still needs a default tag to render the item
                // Also, some properties - like banner base colors - are part of the tag and is processed here.
                BlockEntityTranslator blockEntityTranslator = BlockEntityUtils.getBlockEntityTranslator(type);

                // The Java server can send block entity data for blocks that aren't actually those blocks.
                // A Java client ignores these
                if (type == blockState.block().blockEntityType()) {
                    bedrockBlockEntities.add(blockEntityTranslator.getBlockEntityTag(session, type, x + chunkBlockX, y, z + chunkBlockZ, tag, blockState));

                    // Check for custom skulls
                    if (session.getPreferencesCache().showCustomSkulls() && type == BlockEntityType.SKULL && tag != null && tag.containsKey("profile")) {
                        BlockDefinition blockDefinition = SkullBlockEntityTranslator.translateSkull(session, tag, Vector3i.from(x + chunkBlockX, y, z + chunkBlockZ), blockState);
                        if (blockDefinition != null) {
                            int bedrockSectionY = (y >> 4) - (bedrockDimension.minY() >> 4);
                            int subChunkIndex = (y >> 4) + (bedrockDimension.minY() >> 4);
                            if (0 <= bedrockSectionY && bedrockSectionY < maxBedrockSectionY) {
                                // Custom skull is in a section accepted by Bedrock
                                GeyserChunkSection bedrockSection = sections[bedrockSectionY];
                                IntList palette = bedrockSection.getBlockStorageArray()[0].getPalette();
                                if (palette instanceof IntImmutableList || palette instanceof IntLists.Singleton) {
                                    // TODO there has to be a better way to expand the palette .-.
                                    bedrockSection = bedrockSection.copy(subChunkIndex);
                                    sections[bedrockSectionY] = bedrockSection;
                                }
                                bedrockSection.setFullBlock(x, y & 0xF, z, 0, blockDefinition.getRuntimeId());
                            }
                        }
                    }
                }
            }

            // Find highest section
            sectionCount = sections.length - 1;
            while (sectionCount >= 0 && sections[sectionCount] == null) {
                sectionCount--;
            }
            sectionCount++;

            // As of 1.18.30, the amount of biomes read is dependent on how high Bedrock thinks the dimension is
            int biomeCount = bedrockDimension.height() >> 4;

            // Estimate chunk size
            int size = 0;
            for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    size += section.estimateNetworkSize();
                } else {
                    size += EMPTY_CHUNK_SECTION_SIZE;
                }
            }
            size += ChunkUtils.EMPTY_BIOME_DATA.length * biomeCount;
            size += 1; // Border blocks
            size += bedrockBlockEntities.size() * 64; // Conservative estimate of 64 bytes per tile entity

            // Allocate output buffer
            byteBuf = ByteBufAllocator.DEFAULT.ioBuffer(size);
            for (int i = 0; i < sectionCount; i++) {
                GeyserChunkSection section = sections[i];
                if (section != null) {
                    section.writeToNetwork(byteBuf);
                } else {
                    int subChunkIndex = (i + (bedrockDimension.minY() >> 4));
                    new GeyserChunkSection(EMPTY_BLOCK_STORAGE, subChunkIndex).writeToNetwork(byteBuf);
                }
            }

            int dimensionOffset = bedrockDimension.minY() >> 4;
            for (int i = 0; i < biomeCount; i++) {
                int biomeYOffset = dimensionOffset + i;
                if (biomeYOffset < yOffset) {
                    // Ignore this biome section since it goes below the height of the Java world
                    byteBuf.writeBytes(ChunkUtils.EMPTY_BIOME_DATA);
                    continue;
                }
                if (biomeYOffset >= (chunkSize + yOffset)) {
                    // This biome section goes above the height of the Java world
                    // The byte written here is a header that says to carry on the biome data from the previous chunk
                    byteBuf.writeByte((127 << 1) | 1);
                    continue;
                }

                BiomeTranslator.toNewBedrockBiome(session, javaBiomes[i + (dimensionOffset - yOffset)]).writeToNetwork(byteBuf);
            }

            byteBuf.writeByte(0); // Border blocks - Edu edition only

            // Encode tile entities into buffer
            NBTOutputStream nbtStream = NbtUtils.createNetworkWriter(new ByteBufOutputStream(byteBuf));
            for (NbtMap blockEntity : bedrockBlockEntities) {
                nbtStream.writeTag(blockEntity);
            }
            payload = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(payload);
        } catch (IOException e) {
            GeyserLogger.get().error("IO error while encoding chunk", e);
            return;
        } finally {
            if (byteBuf != null) {
                byteBuf.release(); // Release buffer to allow buffer pooling to be useful
            }
        }

        LevelChunkPacket levelChunkPacket = new LevelChunkPacket();
        levelChunkPacket.setSubChunksLength(sectionCount);
        levelChunkPacket.setCachingEnabled(false);
        levelChunkPacket.setChunkX(packet.getX());
        levelChunkPacket.setChunkZ(packet.getZ());
        levelChunkPacket.setData(Unpooled.wrappedBuffer(payload));
        levelChunkPacket.setDimension(session.getBedrockDimension().bedrockId());
        session.sendUpstreamPacket(levelChunkPacket);

        for (Map.Entry<Vector3i, ItemFrameEntity> entry : session.getItemFrameCache().entrySet()) {
            Vector3i position = entry.getKey();
            if ((position.getX() >> 4) == packet.getX() && (position.getZ() >> 4) == packet.getZ()) {
                // Update this item frame so it doesn't get lost in the abyss
                //TODO optimize
                entry.getValue().updateBlock(true);
            }
        }
    }

    static final class ExtendedCollisionsStorage {
        private int[] data;
        private int sectionY;
    
        int get(int index, int sY) {
            if (data == null) {
                return 0;
            }
            if (!(sY ==  sectionY || sY == sectionY + 1)) {
                data = null;
                return 0;
            }
            return data[index];
        }
    
        void set(int index, int value, int sY) {
            ensureDataExists();
            data[index] = value;
            sectionY = sY;
        }

        void clear() {
            data = null;
        }
    
        int bottomLayerCollisions() {
            if (data == null) {
                return 0;
            }
    
            IntSet uniqueNonZeroSet = new IntOpenHashSet();
            for (int i = 0; i < BlockStorage.SIZE / 16; i++) {
                if (data[i] != 0) {
                    uniqueNonZeroSet.add(data[i]);
                }
            }
            return uniqueNonZeroSet.size();
        }
    
        private void ensureDataExists() {
            if (data == null) {
                data = new int[BlockStorage.SIZE];
            }
        }
    }
}
