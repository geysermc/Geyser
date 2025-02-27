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

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.entity.vehicle.ClientVehicle;
import org.geysermc.geyser.level.physics.Axis;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.level.block.entity.PistonBlockEntity;

import java.util.Map;

@Getter
public class PistonCache {
    @Getter(AccessLevel.PRIVATE)
    private final GeyserSession session;

    /**
     * Maps the position of a piston to its block entity
     */
    private final Map<Vector3i, PistonBlockEntity> pistons = new Object2ObjectOpenHashMap<>();

    /**
     * Maps the position of a moving block to the piston moving it
     * Positions in this map represent the starting position of the block
     */
    private final Map<Vector3i, PistonBlockEntity> movingBlocksMap = new Object2ObjectOpenHashMap<>();

    private Vector3d playerDisplacement = Vector3d.ZERO;

    @Setter
    private Vector3f playerMotion = Vector3f.ZERO;

    /**
     * Stores whether a player has/will collide with any moving blocks.
     */
    @Setter
    private boolean playerCollided = false;

    /**
     * Stores whether a player has/will collide with any slime blocks.
     * This is used to prevent movement from being corrected when players
     * are about to hit a slime block.
     */
    @Setter
    private boolean playerSlimeCollision = false;

    /**
     * Stores whether a player is standing on a honey block.
     * This is used to ignore movement from Bedrock to prevent them from
     * falling off.
     */
    @Setter
    private boolean playerAttachedToHoney = false;

    public PistonCache(GeyserSession session) {
        this.session = session;
    }

    public void tick() {
        resetPlayerMovement();
        if (!pistons.isEmpty()) {
            pistons.values().forEach(PistonBlockEntity::updateMovement);
            sendPlayerMovement();
            sendPlayerMotion();
            // Update blocks after movement, so that players don't get stuck inside blocks
            pistons.values().forEach(PistonBlockEntity::updateBlocks);

            pistons.entrySet().removeIf((entry) -> entry.getValue().canBeRemoved());

            if (pistons.isEmpty() && !movingBlocksMap.isEmpty()) {
                GeyserLogger.get().error("The moving block map has de-synced!");
                for (Map.Entry<Vector3i, PistonBlockEntity> entry : movingBlocksMap.entrySet()) {
                    GeyserLogger.get().error("Moving Block at " + entry.getKey() + " was previously owned by the piston at " + entry.getValue().getPosition());
                }
            }
        }
    }

    private void resetPlayerMovement() {
        playerDisplacement = Vector3d.ZERO;
        playerMotion = Vector3f.ZERO;
        playerCollided = false;
        playerSlimeCollision = false;
        playerAttachedToHoney = false;
    }

    private void sendPlayerMovement() {
        if (!playerDisplacement.equals(Vector3d.ZERO) && playerMotion.equals(Vector3f.ZERO)) {
            SessionPlayerEntity playerEntity = session.getPlayerEntity();

            Entity vehicle = playerEntity.getVehicle();
            if (vehicle instanceof ClientVehicle clientVehicle && clientVehicle.isClientControlled()) {
                return;
            }

            boolean isOnGround = playerDisplacement.getY() > 0 || playerEntity.isOnGround();
            Vector3d position = session.getCollisionManager().getPlayerBoundingBox().getBottomCenter();
            playerEntity.moveAbsolute(position.toFloat(), playerEntity.getYaw(), playerEntity.getPitch(), playerEntity.getHeadYaw(), isOnGround, true);
        }
    }

    private void sendPlayerMotion() {
        if (!playerMotion.equals(Vector3f.ZERO)) {
            SessionPlayerEntity playerEntity = session.getPlayerEntity();

            Entity vehicle = playerEntity.getVehicle();
            if (vehicle instanceof ClientVehicle clientVehicle && clientVehicle.isClientControlled()) {
                vehicle.setMotion(playerMotion);
                return;
            }

            playerEntity.setMotion(playerMotion);

            SetEntityMotionPacket setEntityMotionPacket = new SetEntityMotionPacket();
            setEntityMotionPacket.setRuntimeEntityId(playerEntity.getGeyserId());
            setEntityMotionPacket.setMotion(playerMotion);
            session.sendUpstreamPacket(setEntityMotionPacket);
        }
    }

    /**
     * Add to the player's displacement and move the player's bounding box
     * The total displacement is capped to a range of -0.51 to 0.51 per tick
     *
     * @param displacement The displacement to apply to the player's bounding box
     */
    public void displacePlayer(Vector3d displacement) {
        Vector3d totalDisplacement = playerDisplacement.add(displacement);
        // Clamp to range -0.51 to 0.51
        totalDisplacement = totalDisplacement.max(-0.51d, -0.51d, -0.51d).min(0.51d, 0.51d, 0.51d);

        Vector3d delta = totalDisplacement.sub(playerDisplacement);

        // Check if the piston is pushing a player into collision
        if (session.getPlayerEntity().getVehicle() instanceof ClientVehicle clientVehicle && clientVehicle.isClientControlled()) {
            delta = clientVehicle.getVehicleComponent().correctMovement(delta);
            clientVehicle.getVehicleComponent().moveRelative(delta);
        } else {
            delta = session.getCollisionManager().correctPlayerMovement(delta, true, false);
            session.getCollisionManager().getPlayerBoundingBox().translate(delta.getX(), delta.getY(), delta.getZ());
        }

        playerDisplacement = totalDisplacement;
    }

    /**
     * @param blockPos The block position to test
     * @param boundingBox The bounding box that moves
     * @param axis The axis to apply the offset
     * @param offset The current maximum distance the bounding box can travel
     * @return The new maximum distance the bounding box can travel without colliding with the tested moving block
     */
    public double computeCollisionOffset(Vector3i blockPos, BoundingBox boundingBox, Axis axis, double offset) {
        PistonBlockEntity piston = movingBlocksMap.get(blockPos);
        if (piston != null) {
            return piston.computeCollisionOffset(blockPos, boundingBox, axis, offset);
        }
        return offset;
    }

    public boolean checkCollision(Vector3i blockPos, BoundingBox boundingBox) {
        PistonBlockEntity piston = movingBlocksMap.get(blockPos);
        if (piston != null) {
            return piston.checkCollision(blockPos, boundingBox);
        }
        return false;
    }

    public void clear() {
        pistons.clear();
        movingBlocksMap.clear();
    }
}
