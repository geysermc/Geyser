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

import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.PistonCache;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.block.entity.PistonBlockEntity;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.BellValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.BlockValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.ChestValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.DecoratedPotValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.EndGatewayValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.MobSpawnerValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.NoteBlockValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.PistonValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.PistonValueType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

@Translator(packet = ClientboundBlockEventPacket.class)
public class JavaBlockEventTranslator extends PacketTranslator<ClientboundBlockEventPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundBlockEventPacket packet) {
        Vector3i position = packet.getPosition();
        BlockValue value = packet.getValue();

        if (value == null) {
            GeyserLogger.get().debug("Unable to handle packet %s - null value! ".formatted(packet.toString()));
            return;
        }

        BlockEventPacket blockEventPacket = new BlockEventPacket();
        blockEventPacket.setBlockPosition(position);

        if (value instanceof ChestValue chestValue) {
            blockEventPacket.setEventType(1);
            blockEventPacket.setEventData(chestValue.getViewers() > 0 ? 1 : 0);
            session.sendUpstreamPacket(blockEventPacket);
        } else if (value instanceof EndGatewayValue) {
            blockEventPacket.setEventType(1);
            session.sendUpstreamPacket(blockEventPacket);
        } else if (value instanceof NoteBlockValue) {
            session.getGeyser().getWorldManager().getBlockAtAsync(session, position).thenAccept(blockState -> {
                blockEventPacket.setEventData(BlockState.of(blockState).getValue(Properties.NOTE));
                session.sendUpstreamPacket(blockEventPacket);
            });
        } else if (value instanceof PistonValue pistonValue) {
            PistonValueType action = (PistonValueType) packet.getType();
            Direction direction = Direction.fromPistonValue(pistonValue.getDirection());
            PistonCache pistonCache = session.getPistonCache();

            if (session.getGeyser().getWorldManager().hasOwnChunkCache() || session.getErosionHandler().isActive()) {
                // Mostly handled in the GeyserPistonEvents class (Spigot) / the PistonBlockBaseMixin (Mod platforms)
                // However, the retracting event is not fully covered. (Spigot)
                // Mod platforms only handle pistons moving blocks; not the retracting of pistons.
                if (action == PistonValueType.PULLING || action == PistonValueType.CANCELLED_MID_PUSH) {
                    BlockState pistonBlock = session.getGeyser().getWorldManager().blockAt(session, position);

                    // Retracting sticky pistons is an exception, since the event is not called on Spigot from 1.13.2 - 1.17.1
                    // See https://github.com/PaperMC/Paper/blob/6fa1983e9ce177a4a412d5b950fd978620174777/patches/server/0304-Fire-BlockPistonRetractEvent-for-all-empty-pistons.patch
                    boolean isSticky = isSticky(pistonBlock);
                    if (session.getGeyser().getPlatformType() == PlatformType.SPIGOT && !isSticky) {
                        return;
                    }

                    // Only sticky pistons that don't pull any blocks are affected
                    if (action != PistonValueType.CANCELLED_MID_PUSH && isSticky) {
                        Vector3i blockInFrontPos = position.add(direction.getUnitVector());
                        int blockInFront = session.getGeyser().getWorldManager().getBlockAt(session, blockInFrontPos);
                        if (blockInFront != Block.JAVA_AIR_ID) {
                            // Piston pulled something
                            return;
                        }
                    }
                    PistonBlockEntity blockEntity = pistonCache.getPistons().computeIfAbsent(position, pos -> new PistonBlockEntity(session, pos, direction, isSticky, true));
                    if (blockEntity.getAction() != action) {
                        blockEntity.setAction(action, Object2ObjectMaps.emptyMap());
                    }
                }
            } else {
                PistonBlockEntity blockEntity = pistonCache.getPistons().computeIfAbsent(position, pos -> {
                    BlockState state = session.getGeyser().getWorldManager().blockAt(session, position);
                    boolean sticky = isSticky(state);
                    boolean extended = action != PistonValueType.PUSHING;
                    return new PistonBlockEntity(session, pos, direction, sticky, extended);
                });
                blockEntity.setAction(action);
            }
        } else if (value instanceof MobSpawnerValue) {
            blockEventPacket.setEventType(1);
            session.sendUpstreamPacket(blockEventPacket);
        } else if (value instanceof BellValue bellValue) {
            // Bells - needed to show ring from other players
            BlockEntityDataPacket blockEntityPacket = new BlockEntityDataPacket();
            blockEntityPacket.setBlockPosition(position);

            NbtMapBuilder builder = BlockEntityTranslator.getConstantBedrockTag("Bell", position.getX(), position.getY(), position.getZ());
            int bedrockRingDirection = switch (bellValue.getDirection()) {
                case SOUTH -> 0;
                case WEST -> 1;
                case NORTH -> 2;
                case EAST -> 3;
                default -> throw new IllegalStateException("Unexpected BellValue Direction: " + bellValue.getDirection());
            };
            builder.putInt("Direction", bedrockRingDirection);
            builder.putByte("Ringing", (byte) 1);
            builder.putInt("Ticks", 0);
            
            blockEntityPacket.setData(builder.build());
            session.sendUpstreamPacket(blockEntityPacket);
        } else if (value instanceof DecoratedPotValue potValue) {
            // Decorated pots - wobble wobble
            // We need to send the sherd data with the client, but we don't really care about latency here so we
            // can safely get this from the server
            session.getGeyser().getWorldManager().getDecoratedPotData(session, position, sherds -> {
                BlockEntityDataPacket blockEntityPacket = new BlockEntityDataPacket();
                blockEntityPacket.setBlockPosition(position);

                NbtMapBuilder builder = BlockEntityTranslator.getConstantBedrockTag("DecoratedPot", position);
                builder.putList("sherds", NbtType.STRING, sherds);
                builder.putByte("animation", switch (potValue.getWobbleStyle()) {
                    case POSITIVE -> (byte) 2;
                    case NEGATIVE -> (byte) 1;
                });

                blockEntityPacket.setData(builder.build());
                session.sendUpstreamPacket(blockEntityPacket);
            });
        } else if (GeyserLogger.get().isDebug()) {
            GeyserLogger.get().debug("Unhandled block event packet: " + packet);
        }
    }

    private static boolean isSticky(BlockState state) {
        return state.is(Blocks.STICKY_PISTON) || (state.is(Blocks.MOVING_PISTON) && "sticky".equals(state.getValue(Properties.PISTON_TYPE)));
    }
}
