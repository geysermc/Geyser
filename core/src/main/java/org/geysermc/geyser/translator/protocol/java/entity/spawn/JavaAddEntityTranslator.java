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

package org.geysermc.geyser.translator.protocol.java.entity.spawn;

import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.FallingBlockEntity;
import org.geysermc.geyser.entity.type.FishingHookEntity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.PaintingEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.EnvironmentUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.FallingBlockData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.ProjectileData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.WardenData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;

@Translator(packet = ClientboundAddEntityPacket.class)
public class JavaAddEntityTranslator extends PacketTranslator<ClientboundAddEntityPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundAddEntityPacket packet) {
        EntityDefinition<?> definition = Registries.ENTITY_DEFINITIONS.get(packet.getType());
        if (definition == null) {
            GeyserLogger.get().debug("Could not find an entity definition with type " + packet.getType());
            return;
        }

        Vector3f position = Vector3f.from(packet.getX(), packet.getY(), packet.getZ());
        Vector3f motion = Vector3f.from(packet.getMotionX(), packet.getMotionY(), packet.getMotionZ());
        float yaw = packet.getYaw();
        float pitch = packet.getPitch();
        float headYaw = packet.getHeadYaw();

        if (packet.getType() == EntityType.PLAYER) {

            PlayerEntity entity;
            if (packet.getUuid().equals(session.getPlayerEntity().getUuid())) {
                // Server is sending a fake version of the current player
                entity = new PlayerEntity(session, packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(),
                        session.getPlayerEntity().getUuid(), position, motion, yaw, pitch, headYaw, session.getPlayerEntity().getUsername(),
                        session.getPlayerEntity().getTexturesProperty());
            } else {
                entity = session.getEntityCache().getPlayerEntity(packet.getUuid());
                if (entity == null) {
                    GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.entity.player.failed_list", packet.getUuid()));
                    return;
                }

                entity.setEntityId(packet.getEntityId());
                entity.setPosition(position);
                entity.setYaw(yaw);
                entity.setPitch(pitch);
                entity.setHeadYaw(headYaw);
                entity.setMotion(motion);
            }

            entity.sendPlayer();
            // only load skin if we're not in a test environment.
            // Otherwise, it tries to load various resources
            if (!EnvironmentUtils.IS_UNIT_TESTING) {
                SkinManager.requestAndHandleSkinAndCape(entity, session, null);
            }
            return;
        }

        Entity entity;
        if (packet.getType() == EntityType.FALLING_BLOCK) {
            entity = new FallingBlockEntity(session, packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(), packet.getUuid(),
                    position, motion, yaw, pitch, headYaw, ((FallingBlockData) packet.getData()).getId());
        } else if (packet.getType() == EntityType.ITEM_FRAME || packet.getType() == EntityType.GLOW_ITEM_FRAME) {
            // Item frames need the hanging direction
            entity = new ItemFrameEntity(session, packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(), packet.getUuid(),
                    definition, position, motion, yaw, pitch, headYaw, (Direction) packet.getData());
        } else if (packet.getType() == EntityType.PAINTING) {
            entity = new PaintingEntity(session, packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(), packet.getUuid(),
                    definition, position, motion, yaw, pitch, headYaw, (Direction) packet.getData());
        } else if (packet.getType() == EntityType.FISHING_BOBBER) {
            // Fishing bobbers need the owner for the line
            int ownerEntityId = ((ProjectileData) packet.getData()).getOwnerId();
            Entity owner = session.getEntityCache().getEntityByJavaId(ownerEntityId);
            // Java clients only spawn fishing hooks with a player as its owner
            if (owner instanceof PlayerEntity) {
                entity = new FishingHookEntity(session, packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(), packet.getUuid(),
                        position, motion, yaw, pitch, headYaw, (PlayerEntity) owner);
            } else {
                return;
            }
        } else {
            entity = definition.factory().create(session, packet.getEntityId(), session.getEntityCache().getNextEntityId().incrementAndGet(),
                    packet.getUuid(), definition, position, motion, yaw, pitch, headYaw);
        }

        if (packet.getType() == EntityType.WARDEN) {
            WardenData wardenData = (WardenData) packet.getData();
            if (wardenData.isEmerging()) {
                entity.setPose(Pose.EMERGING);
            }
        }

        session.getEntityCache().spawnEntity(entity);
    }
}
