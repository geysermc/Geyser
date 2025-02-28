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

package org.geysermc.geyser.translator.protocol.java.entity.player;

import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.TeleportCache;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

@Translator(packet = ClientboundPlayerPositionPacket.class)
public class JavaPlayerPositionTranslator extends PacketTranslator<ClientboundPlayerPositionPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundPlayerPositionPacket packet) {
        if (!session.isLoggedIn())
            return;

        SessionPlayerEntity entity = session.getPlayerEntity();
        Vector3d pos = packet.getPosition();

        if (!session.isSpawned()) {
            // TODO this behavior seems outdated (1.21.2).
            // The server sends an absolute teleport everytime the player is respawned
            entity.setPosition(pos.toFloat());
            entity.setYaw(packet.getYRot());
            entity.setPitch(packet.getXRot());
            entity.setHeadYaw(packet.getYRot());

            RespawnPacket respawnPacket = new RespawnPacket();
            respawnPacket.setRuntimeEntityId(0); // Bedrock server behavior
            respawnPacket.setPosition(entity.getPosition());
            respawnPacket.setState(RespawnPacket.State.SERVER_READY);
            session.sendUpstreamPacket(respawnPacket);

            entity.updateBedrockMetadata();

            MovePlayerPacket movePlayerPacket = new MovePlayerPacket();
            movePlayerPacket.setRuntimeEntityId(entity.getGeyserId());
            movePlayerPacket.setPosition(entity.getPosition());
            movePlayerPacket.setRotation(entity.getBedrockRotation());
            movePlayerPacket.setMode(MovePlayerPacket.Mode.RESPAWN);

            session.sendUpstreamPacket(movePlayerPacket);

            // Fixes incorrect rotation upon login
            // Yes, even that's not respected by Bedrock. Try it out in singleplayer!
            // Log out and back in - and you're looking elsewhere :)
            entity.updateOwnRotation(entity.getYaw(), entity.getPitch(), entity.getHeadYaw());

            session.setSpawned(true);
            // Make sure the player moves away from (0, 32767, 0) before accepting movement packets
            session.setUnconfirmedTeleport(new TeleportCache(packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ(), packet.getXRot(), packet.getYRot(), packet.getId())); // TODO

            acceptTeleport(session, packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ(), packet.getYRot(), packet.getXRot(), packet.getId());

            if (session.getServerRenderDistance() > 32 && !session.isEmulatePost1_13Logic()) {
                // See DimensionUtils for an explanation
                ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
                chunkRadiusUpdatedPacket.setRadius(session.getServerRenderDistance());
                session.sendUpstreamPacket(chunkRadiusUpdatedPacket);

                session.setLastChunkPosition(null);
            }

            ChunkUtils.updateChunkPosition(session, pos.toInt());

            if (session.getGeyser().config().debugMode()) {
                session.getGeyser().getLogger().debug("Spawned player at " + packet.getPosition());
            }
            return;
        }

        // If coordinates are relative, then add to the existing coordinate
        double newX = pos.getX() +
                (packet.getRelatives().contains(PositionElement.X) ? entity.getPosition().getX() : 0);
        double newY = pos.getY() +
                (packet.getRelatives().contains(PositionElement.Y) ? entity.getPosition().getY() - EntityDefinitions.PLAYER.offset() : 0);
        double newZ = pos.getZ() +
                (packet.getRelatives().contains(PositionElement.Z) ? entity.getPosition().getZ() : 0);

        float newPitch = packet.getXRot() + (packet.getRelatives().contains(PositionElement.X_ROT) ? entity.getPitch() : 0);
        float newYaw = packet.getYRot() + (packet.getRelatives().contains(PositionElement.Y_ROT) ? entity.getYaw() : 0);

        int id = packet.getId();

        session.getGeyser().getLogger().debug("Teleport (" + id + ") from " + entity.getPosition().getX() + " " + (entity.getPosition().getY() - EntityDefinitions.PLAYER.offset()) + " " + entity.getPosition().getZ());

        Vector3f lastPlayerPosition = entity.getPosition().down(EntityDefinitions.PLAYER.offset());
        float lastPlayerPitch = entity.getPitch();
        Vector3f teleportDestination = Vector3f.from(newX, newY, newZ);
        entity.moveAbsolute(teleportDestination, newYaw, newPitch, true, true);

        session.getGeyser().getLogger().debug("to " + entity.getPosition().getX() + " " + (entity.getPosition().getY() - EntityDefinitions.PLAYER.offset()) + " " + entity.getPosition().getZ());

        // Bedrock ignores teleports that are extremely close to the player's original position and orientation,
        // so check if we need to cache the teleport
        if (lastPlayerPosition.distanceSquared(teleportDestination) < 0.001 && Math.abs(newPitch - lastPlayerPitch) < 5) {
            session.setUnconfirmedTeleport(null);
        } else {
            session.setUnconfirmedTeleport(new TeleportCache(newX, newY, newZ, newPitch, newYaw, id));
        }

        acceptTeleport(session, newX, newY, newZ, newYaw, newPitch, id);
    }

    private void acceptTeleport(GeyserSession session, double x, double y, double z, float yaw, float pitch, int id) {
        // Confirm the teleport when we receive it to match Java edition
        ServerboundAcceptTeleportationPacket teleportConfirmPacket = new ServerboundAcceptTeleportationPacket(id);
        session.sendDownstreamGamePacket(teleportConfirmPacket);
        // Servers (especially ones like Hypixel) expect exact coordinates given back to them.
        ServerboundMovePlayerPosRotPacket positionPacket = new ServerboundMovePlayerPosRotPacket(false, false, x, y, z, yaw, pitch);
        session.sendDownstreamGamePacket(positionPacket);
    }
}
