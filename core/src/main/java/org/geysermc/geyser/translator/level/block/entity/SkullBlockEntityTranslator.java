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

package org.geysermc.geyser.translator.level.block.entity;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@BlockEntity(type = BlockEntityType.SKULL)
public class SkullBlockEntityTranslator extends BlockEntityTranslator implements RequiresBlockState {
    @Override
    public void translateTag(GeyserSession session, NbtMapBuilder bedrockNbt, NbtMap javaNbt, BlockState blockState) {
        Integer rotation = blockState.getValue(Properties.ROTATION_16);
        if (rotation != null) {
            // Could be a wall skull block otherwise, which has rotation in its Bedrock state
            bedrockNbt.putFloat("Rotation", rotation * 22.5f);
        }
        if (blockState.getValue(Properties.POWERED)) {
            bedrockNbt.putBoolean("MouthMoving", true);
        }
    }

    private static UUID getUUID(NbtMap profile) {
        int[] uuidAsArray = profile.getIntArray("id");
        if (uuidAsArray.length == 4) {
            // thank u viaversion
            return new UUID((long) uuidAsArray[0] << 32 | ((long) uuidAsArray[1] & 0xFFFFFFFFL),
                    (long) uuidAsArray[2] << 32 | ((long) uuidAsArray[3] & 0xFFFFFFFFL));
        }
        // Convert username to an offline UUID
        String username = null;
        String nameTag = profile.getString("name", null);
        if (nameTag != null) {
            username = nameTag.toLowerCase(Locale.ROOT);
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static CompletableFuture<@Nullable String> getTextures(NbtMap profile, UUID uuid) {
        List<NbtMap> properties = profile.getList("properties", NbtType.COMPOUND);
        if (properties.isEmpty()) {
            if (uuid != null && uuid.version() == 4) {
                String uuidString = uuid.toString().replace("-", "");
                return SkinProvider.requestTexturesFromUUID(uuidString);
            } else {
                String nameTag = profile.getString("name", null);
                if (nameTag != null) {
                    // Fall back to username if UUID was missing or was an offline mode UUID
                    return SkinProvider.requestTexturesFromUsername(nameTag);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        NbtMap tag1 = properties.get(0);
        String texture = tag1.getString("value", null);
        return CompletableFuture.completedFuture(texture);
    }

    public static @Nullable BlockDefinition translateSkull(GeyserSession session, NbtMap javaNbt, Vector3i blockPosition, BlockState blockState) {
        NbtMap profile = javaNbt.getCompound("profile");
        if (profile.isEmpty()) {
            session.getSkullCache().removeSkull(blockPosition);
            return null;
        }
        UUID uuid = getUUID(profile);

        CompletableFuture<String> texturesFuture = getTextures(profile, uuid);
        if (texturesFuture.isDone()) {
            try {
                String texture = texturesFuture.get();
                if (texture == null) {
                    GeyserLogger.get().debug("Custom skull with invalid profile tag: " + blockPosition + " " + javaNbt);
                    return null;
                }
                SkullCache.Skull skull = session.getSkullCache().putSkull(blockPosition, uuid, texture, blockState);
                return skull.getBlockDefinition();
            } catch (InterruptedException | ExecutionException e) {
                GeyserLogger.get().debug("Failed to acquire textures for custom skull: " + blockPosition + " " + javaNbt);
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        // profile contained a username, so we have to wait for it to be retrieved
        texturesFuture.whenComplete((texturesProperty, throwable) -> {
            if (texturesProperty == null) {
                GeyserLogger.get().debug("Custom skull with invalid profile tag: " + blockPosition + " " + javaNbt);
                return;
            }

            session.ensureInEventLoop(() -> putSkull(session, blockPosition, uuid, texturesProperty, blockState));
        });

        // We don't have the textures yet, so we can't determine if a custom block was defined for this skull
        return null;
    }

    private static void putSkull(GeyserSession session, Vector3i blockPosition, UUID uuid, String texturesProperty, BlockState blockState) {
        SkullCache.Skull skull = session.getSkullCache().putSkull(blockPosition, uuid, texturesProperty, blockState);
        if (skull.getBlockDefinition() != null) {
            UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
            updateBlockPacket.setDataLayer(0);
            updateBlockPacket.setBlockPosition(blockPosition);
            updateBlockPacket.setDefinition(skull.getBlockDefinition());
            updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
            updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
            session.sendUpstreamPacket(updateBlockPacket);
        }
    }
}
