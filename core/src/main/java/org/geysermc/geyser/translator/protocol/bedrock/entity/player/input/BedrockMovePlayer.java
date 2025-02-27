/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.protocol.bedrock.entity.player.input;

import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.level.physics.CollisionResult;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;

/**
 * Holds processing input coming in from the {@link PlayerAuthInputPacket} packet.
 */
final class BedrockMovePlayer {

    static void translate(GeyserSession session, PlayerAuthInputPacket packet) {
        SessionPlayerEntity entity = session.getPlayerEntity();
        if (!session.isSpawned()) return;

        // Ignore movement packets until Bedrock's position matches the teleported position
        if (session.getUnconfirmedTeleport() != null) {
            session.confirmTeleport(packet.getPosition().toDouble().sub(0, EntityDefinitions.PLAYER.offset(), 0));
            return;
        }

        boolean actualPositionChanged = !entity.getPosition().equals(packet.getPosition());

        if (actualPositionChanged) {
            // Send book update before the player moves
            session.getBookEditCache().checkForSend();
        }

        if (entity.getBedPosition() != null) {
            // https://github.com/GeyserMC/Geyser/issues/5001
            // Bedrock 1.21.22 started sending a MovePlayerPacket as soon as it got into a bed.
            // This trips up Fabric.
            return;
        }

        float yaw = packet.getRotation().getY();
        float pitch = packet.getRotation().getX();
        float headYaw = packet.getRotation().getY();

        boolean hasVehicle = entity.getVehicle() != null;

        // shouldSendPositionReminder also increments a tick counter, so make sure it's always called unless the player is on a vehicle.
        boolean positionChanged = !hasVehicle && (session.getInputCache().shouldSendPositionReminder() || actualPositionChanged);
        boolean rotationChanged = hasVehicle || (entity.getYaw() != yaw || entity.getPitch() != pitch || entity.getHeadYaw() != headYaw);

        if (session.getLookBackScheduledFuture() != null) {
            // Resend the rotation if it was changed by Geyser
            rotationChanged |= !session.getLookBackScheduledFuture().isDone();
            session.getLookBackScheduledFuture().cancel(false);
            session.setLookBackScheduledFuture(null);
        }

        // Client is telling us it wants to move down, but something is blocking it from doing so.
        boolean isOnGround = packet.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION) && packet.getDelta().getY() < 0;
        // This takes into account no movement sent from the client, but the player is trying to move anyway.
        // (Press into a wall in a corner - you're trying to move but nothing actually happens)
        boolean horizontalCollision = packet.getInputData().contains(PlayerAuthInputData.HORIZONTAL_COLLISION);

        // If only the pitch and yaw changed
        // This isn't needed, but it makes the packets closer to vanilla
        // It also means you can't "lag back" while only looking, in theory
        if (!positionChanged && rotationChanged) {
            ServerboundMovePlayerRotPacket playerRotationPacket = new ServerboundMovePlayerRotPacket(isOnGround, horizontalCollision, yaw, pitch);

            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.setHeadYaw(headYaw);

            session.sendDownstreamGamePacket(playerRotationPacket);
        } else if (positionChanged) {
            if (isValidMove(session, entity.getPosition(), packet.getPosition())) {
                CollisionResult result = session.getCollisionManager().adjustBedrockPosition(packet.getPosition(), isOnGround, packet.getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT));
                if (result != null) { // A null return value cancels the packet
                    Vector3d position = result.correctedMovement();
                    boolean isBelowVoid = entity.isVoidPositionDesynched();

                    boolean teleportThroughVoidFloor, mustResyncPosition;
                    // Compare positions here for void floor fix below before the player's position variable is set to the packet position
                    if (entity.getPosition().getY() >= packet.getPosition().getY() && !isBelowVoid) {
                        int floorY = position.getFloorY();
                        int voidFloorLocation = entity.voidFloorPosition();
                        teleportThroughVoidFloor = floorY <= (voidFloorLocation + 1) && floorY >= voidFloorLocation;
                    } else {
                        teleportThroughVoidFloor = false;
                    }

                    if (teleportThroughVoidFloor || isBelowVoid) {
                        // https://github.com/GeyserMC/Geyser/issues/3521 - no void floor in Java so we cannot be on the ground.
                        isOnGround = false;
                    }

                    if (isBelowVoid) {
                        int floorY = position.getFloorY();
                        int voidFloorLocation = entity.voidFloorPosition();
                        mustResyncPosition = floorY < voidFloorLocation && floorY >= voidFloorLocation - 1;
                    } else {
                        mustResyncPosition = false;
                    }

                    double yPosition = position.getY();
                    if (entity.isVoidPositionDesynched()) { // not using the cached variable on purpose
                        yPosition += 4; // We are de-synched since we had to teleport below the void floor.
                    }

                    Packet movePacket;
                    if (rotationChanged) {
                        // Send rotation updates as well
                        movePacket = new ServerboundMovePlayerPosRotPacket(
                                isOnGround,
                                horizontalCollision,
                                position.getX(), yPosition, position.getZ(),
                                yaw, pitch
                        );
                        entity.setYaw(yaw);
                        entity.setPitch(pitch);
                        entity.setHeadYaw(headYaw);
                    } else {
                        // Rotation did not change; don't send an update with rotation
                        movePacket = new ServerboundMovePlayerPosPacket(isOnGround, horizontalCollision, position.getX(), yPosition, position.getZ());
                    }

                    entity.setPositionManual(packet.getPosition());

                    // Send final movement changes
                    session.sendDownstreamGamePacket(movePacket);

                    if (teleportThroughVoidFloor) {
                        entity.teleportVoidFloorFix(false);
                    } else if (mustResyncPosition) {
                        entity.teleportVoidFloorFix(true);
                    }

                    session.getInputCache().markPositionPacketSent();
                    session.getSkullCache().updateVisibleSkulls();
                }
            } else {
                // Not a valid move
                GeyserLogger.get().debug("Recalculating position...");
                session.getCollisionManager().recalculatePosition();
            }
        } else if (horizontalCollision != session.getInputCache().lastHorizontalCollision() || isOnGround != entity.isOnGround()) {
            session.sendDownstreamGamePacket(new ServerboundMovePlayerStatusOnlyPacket(isOnGround, horizontalCollision));
        }

        session.getInputCache().setLastHorizontalCollision(horizontalCollision);
        entity.setOnGround(isOnGround);

        // Move parrots to match if applicable
        if (entity.getLeftParrot() != null) {
            entity.getLeftParrot().moveAbsolute(entity.getPosition(), entity.getYaw(), entity.getPitch(), entity.getHeadYaw(), true, false);
        }
        if (entity.getRightParrot() != null) {
            entity.getRightParrot().moveAbsolute(entity.getPosition(), entity.getYaw(), entity.getPitch(), entity.getHeadYaw(), true, false);
        }
    }

    private static boolean isInvalidNumber(float val) {
        return Float.isNaN(val) || Float.isInfinite(val);
    }

    private static boolean isValidMove(GeyserSession session, Vector3f currentPosition, Vector3f newPosition) {
        if (isInvalidNumber(newPosition.getX()) || isInvalidNumber(newPosition.getY()) || isInvalidNumber(newPosition.getZ())) {
            return false;
        }
        if (currentPosition.distanceSquared(newPosition) > 300) {
            GeyserLogger.get().debug(ChatColor.RED + session.bedrockUsername() + " moved too quickly." +
                    " current position: " + currentPosition + ", new position: " + newPosition);

            return false;
        }

        return true;
    }
}

