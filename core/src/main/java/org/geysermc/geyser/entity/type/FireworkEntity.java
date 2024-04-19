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

package org.geysermc.geyser.entity.type;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.level.FireworkColor;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public class FireworkEntity extends Entity {

    public FireworkEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setFireworkItem(EntityMetadata<ItemStack, ?> entityMetadata) {
        ItemStack item = entityMetadata.getValue();
        if (item == null) {
            return;
        }
        CompoundTag tag = item.getNbt();

        if (tag == null) {
            return;
        }

        // TODO: Remove once Mojang fixes bugs with fireworks crashing clients on these specific devices.
        // https://bugs.mojang.com/browse/MCPE-89115
        if (session.getClientData().getDeviceOs() == DeviceOs.XBOX
                || session.getClientData().getDeviceOs() == DeviceOs.PS4) {
            return;
        }

        CompoundTag fireworks = tag.get("Fireworks");
        if (fireworks == null) {
            // Thank you Mineplex very cool
            return;
        }

        NbtMapBuilder fireworksBuilder = NbtMap.builder();
        if (fireworks.get("Flight") != null) {
            fireworksBuilder.putByte("Flight", MathUtils.getNbtByte(fireworks.get("Flight").getValue()));
        }

        List<NbtMap> explosions = new ArrayList<>();
        if (fireworks.get("Explosions") != null) {
            for (Tag effect : ((ListTag) fireworks.get("Explosions")).getValue()) {
                CompoundTag effectData = (CompoundTag) effect;
                NbtMapBuilder effectBuilder = NbtMap.builder();

                if (effectData.get("Type") != null) {
                    effectBuilder.putByte("FireworkType", MathUtils.getNbtByte(effectData.get("Type").getValue()));
                }

                if (effectData.get("Colors") != null) {
                    int[] oldColors = (int[]) effectData.get("Colors").getValue();
                    byte[] colors = new byte[oldColors.length];

                    int i = 0;
                    for (int color : oldColors) {
                        colors[i++] = FireworkColor.fromJavaRGB(color);
                    }

                    effectBuilder.putByteArray("FireworkColor", colors);
                }

                if (effectData.get("FadeColors") != null) {
                    int[] oldColors = (int[]) effectData.get("FadeColors").getValue();
                    byte[] colors = new byte[oldColors.length];

                    int i = 0;
                    for (int color : oldColors) {
                        colors[i++] = FireworkColor.fromJavaRGB(color);
                    }

                    effectBuilder.putByteArray("FireworkFade", colors);
                }

                if (effectData.get("Trail") != null) {
                    effectBuilder.putByte("FireworkTrail", MathUtils.getNbtByte(effectData.get("Trail").getValue()));
                }

                if (effectData.get("Flicker") != null) {
                    effectBuilder.putByte("FireworkFlicker", MathUtils.getNbtByte(effectData.get("Flicker").getValue()));
                }

                explosions.add(effectBuilder.build());
            }
        }

        fireworksBuilder.putList("Explosions", NbtType.COMPOUND, explosions);

        NbtMapBuilder builder = NbtMap.builder();
        builder.put("Fireworks", fireworksBuilder.build());
        dirtyMetadata.put(EntityDataTypes.DISPLAY_FIREWORK, builder.build());
    }

    public void setPlayerGliding(EntityMetadata<OptionalInt, ?> entityMetadata) {
        OptionalInt optional = entityMetadata.getValue();
        // Checks if the firework has an entity ID (used when a player is gliding)
        // and checks to make sure the player that is gliding is the one getting sent the packet
        // or else every player near the gliding player will boost too.
        if (optional.isPresent() && optional.getAsInt() == session.getPlayerEntity().getEntityId()) {
            PlayerEntity entity = session.getPlayerEntity();
            float yaw = entity.getYaw();
            float pitch = entity.getPitch();
            // Uses math from NukkitX
            entity.setMotion(Vector3f.from(
                    -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 2,
                    -Math.sin(Math.toRadians(pitch)) * 2,
                    Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 2));
            // Need to update the EntityMotionPacket or else the player won't boost
            SetEntityMotionPacket entityMotionPacket = new SetEntityMotionPacket();
            entityMotionPacket.setRuntimeEntityId(entity.getGeyserId());
            entityMotionPacket.setMotion(entity.getMotion());

            session.sendUpstreamPacket(entityMotionPacket);
        }
    }
}
