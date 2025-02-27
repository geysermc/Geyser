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

package org.geysermc.geyser.erosion;

import io.netty.channel.Channel;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.geysermc.erosion.packet.ErosionPacketHandler;
import org.geysermc.erosion.packet.ErosionPacketSender;
import org.geysermc.erosion.packet.backendbound.BackendboundInitializePacket;
import org.geysermc.erosion.packet.backendbound.BackendboundPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBatchBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockEntityPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockLookupFailPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockPlacePacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundHandshakePacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPickBlockPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPistonEventPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.PistonCache;
import org.geysermc.geyser.translator.level.block.entity.PistonBlockEntity;
import org.geysermc.geyser.util.BlockEntityUtils;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.value.PistonValueType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class GeyserboundPacketHandlerImpl extends AbstractGeyserboundPacketHandler {
    private final ErosionPacketSender<BackendboundPacket> packetSender;
    @Setter
    private CompletableFuture<Integer> pendingLookup = null;
    @Getter
    private final Int2ObjectMap<CompletableFuture<Integer>> asyncPendingLookups = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(4));
    @Setter
    private CompletableFuture<int[]> pendingBatchLookup = null;
    @Setter
    private CompletableFuture<Int2ObjectMap<byte[]>> pickBlockLookup = null;

    private final AtomicInteger nextTransactionId = new AtomicInteger(1);

    public GeyserboundPacketHandlerImpl(GeyserSession session, ErosionPacketSender<BackendboundPacket> packetSender) {
        super(session);
        this.packetSender = packetSender;
    }

    @Override
    public void handleBatchBlockId(GeyserboundBatchBlockIdPacket packet) {
        if (this.pendingBatchLookup != null) {
            this.pendingBatchLookup.complete(packet.getBlocks());
        } else {
            GeyserLogger.get().warning("Batch block ID packet received with no future to complete.");
        }
    }

    @Override
    public void handleBlockEntity(GeyserboundBlockEntityPacket packet) {
        NbtMap nbt = packet.getNbt();
        BlockEntityUtils.updateBlockEntity(session, nbt, Vector3i.from(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z")));
    }

    @Override
    public void handleBlockId(GeyserboundBlockIdPacket packet) {
        if (packet.getTransactionId() == 0) {
            if (this.pendingLookup != null) {
                this.pendingLookup.complete(packet.getBlockId());
                return;
            }
        }
        CompletableFuture<Integer> future = this.asyncPendingLookups.remove(packet.getTransactionId());
        if (future != null) {
            future.complete(packet.getBlockId());
            return;
        }
        GeyserLogger.get().warning("Block ID packet received with no future to complete.");
    }

    @Override
    public void handleBlockLookupFail(GeyserboundBlockLookupFailPacket packet) {
        if (packet.getTransactionId() == 0) {
            if (this.pendingBatchLookup != null) {
                this.pendingBatchLookup.complete(null);
                return;
            }
        }
        int transactionId = packet.getTransactionId() - 1;
        if (transactionId == 0) {
            if (this.pendingLookup != null) {
                this.pendingLookup.complete(0);
            }
        }
        CompletableFuture<Integer> future = this.asyncPendingLookups.remove(transactionId);
        if (future != null) {
            future.complete(Block.JAVA_AIR_ID);
        }
    }

    @Override
    public void handleBlockPlace(GeyserboundBlockPlacePacket packet) {
        LevelSoundEventPacket placeBlockSoundPacket = new LevelSoundEventPacket();
        placeBlockSoundPacket.setSound(SoundEvent.PLACE);
        placeBlockSoundPacket.setPosition(packet.getPos().toFloat());
        placeBlockSoundPacket.setBabySound(false);
        placeBlockSoundPacket.setExtraData(session.getBlockMappings().getBedrockBlockId(packet.getBlockId()));
        placeBlockSoundPacket.setIdentifier(":");
        session.sendUpstreamPacket(placeBlockSoundPacket);
        session.setLastBlockPlacePosition(null);
        session.setLastBlockPlaced(null);
    }

    @Override
    public void handlePickBlock(GeyserboundPickBlockPacket packet) {
        if (this.pickBlockLookup != null) {
            this.pickBlockLookup.complete(packet.getComponents());
        }
    }

    @Override
    public void handlePistonEvent(GeyserboundPistonEventPacket packet) {
        Direction orientation = BlockState.of(packet.getBlockId()).getValue(Properties.FACING);
        Vector3i position = packet.getPos();
        boolean isExtend = packet.isExtend();

        var stream = packet.getAttachedBlocks()
                .object2IntEntrySet()
                .stream()
                .map(entry -> Pair.of(entry.getKey(), BlockState.of(entry.getIntValue())))
                .filter(pair -> BlockStateValues.canPistonMoveBlock(pair.value(), isExtend));
        Object2ObjectMap<Vector3i, BlockState> attachedBlocks = new Object2ObjectArrayMap<>();
        stream.forEach(pair -> attachedBlocks.put(pair.key(), pair.value()));

        session.executeInEventLoop(() -> {
            PistonCache pistonCache = session.getPistonCache();
            PistonBlockEntity blockEntity = pistonCache.getPistons().computeIfAbsent(position, pos ->
                    new PistonBlockEntity(session, position, orientation, packet.isSticky(), !isExtend));
            blockEntity.setAction(isExtend ? PistonValueType.PUSHING : PistonValueType.PULLING, attachedBlocks);
        });
    }

    @Override
    public void handleHandshake(GeyserboundHandshakePacket packet) {
        var handler = new GeyserboundHandshakePacketHandler(this.session);
        session.setErosionHandler(handler);
        handler.handleHandshake(packet);
        this.close();
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public GeyserboundPacketHandlerImpl getAsActive() {
        return this;
    }

    @Override
    public void onConnect() {
        sendPacket(new BackendboundInitializePacket(session.getPlayerEntity().getUuid(), GameProtocol.getJavaProtocolVersion()));
    }

    public void sendPacket(BackendboundPacket packet) {
        this.packetSender.sendPacket(packet);
    }

    public void close() {
        this.packetSender.close();

        if (pendingLookup != null) {
            pendingLookup.completeExceptionally(new ErosionCancellationException());
        }
        if (pendingBatchLookup != null) {
            pendingBatchLookup.completeExceptionally(new ErosionCancellationException());
        }
        if (pickBlockLookup != null) {
            pickBlockLookup.completeExceptionally(new ErosionCancellationException());
        }
        asyncPendingLookups.forEach(($, future) -> future.completeExceptionally(new ErosionCancellationException()));
    }

    public int getNextTransactionId() {
        return nextTransactionId.getAndIncrement();
    }

    @Override
    public ErosionPacketHandler setChannel(Channel channel) {
        this.packetSender.setChannel(channel);
        return this;
    }
}
