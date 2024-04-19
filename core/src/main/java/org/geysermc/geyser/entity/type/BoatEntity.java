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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import lombok.Getter;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BoatEntity extends Entity {

    /**
     * Required when IS_BUOYANT is sent in order for boats to work in the water. <br>
     *
     * Taken from BDS 1.16.200, with the modification of <code>simulate_waves</code> since Java doesn't bob the boat up and down
     * like Bedrock.
     */
    private static final String BUOYANCY_DATA = "{\"apply_gravity\":true,\"base_buoyancy\":1.0,\"big_wave_probability\":0.02999999932944775," +
            "\"big_wave_speed\":10.0,\"drag_down_on_buoyancy_removed\":0.0,\"liquid_blocks\":[\"minecraft:water\"," +
            "\"minecraft:flowing_water\"],\"simulate_waves\":false}";

    private boolean isPaddlingLeft;
    private float paddleTimeLeft;
    private boolean isPaddlingRight;
    private float paddleTimeRight;

    /**
     * Saved for using the "pick" functionality on a boat.
     */
    @Getter
    private int variant;

    // Looks too fast and too choppy with 0.1f, which is how I believe the Microsoftian client handles it
    private final float ROWING_SPEED = 0.1f;

    public BoatEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        // Initial rotation is incorrect
        super(session, entityId, geyserId, uuid, definition, position.add(0d, definition.offset(), 0d), motion, yaw + 90, 0, yaw + 90);

        // Required to be able to move on land 1.16.200+ or apply gravity not in the water 1.16.100+
        dirtyMetadata.put(EntityDataTypes.IS_BUOYANT, true);
        dirtyMetadata.put(EntityDataTypes.BUOYANCY_DATA, BUOYANCY_DATA);
    }

    @Override
    public void moveAbsolute(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        // We don't include the rotation (y) as it causes the boat to appear sideways
        setPosition(position.add(0d, this.definition.offset(), 0d));
        setYaw(yaw + 90);
        setHeadYaw(yaw + 90);
        setOnGround(isOnGround);

        MoveEntityAbsolutePacket moveEntityPacket = new MoveEntityAbsolutePacket();
        moveEntityPacket.setRuntimeEntityId(geyserId);
        if (session.getPlayerEntity().getVehicle() == this && session.getPlayerEntity().isRidingInFront()) {
            // Minimal glitching when ClientboundMoveVehiclePacket is sent
            moveEntityPacket.setPosition(position.up(EntityDefinitions.PLAYER.offset() - this.definition.offset()));
        } else {
            moveEntityPacket.setPosition(this.position);
        }
        moveEntityPacket.setRotation(getBedrockRotation());
        moveEntityPacket.setOnGround(isOnGround);
        moveEntityPacket.setTeleported(teleported);

        session.sendUpstreamPacket(moveEntityPacket);
    }

    /**
     * Move the boat without making the adjustments needed to translate from Java
     */
    public void moveAbsoluteWithoutAdjustments(Vector3f position, float yaw, boolean isOnGround, boolean teleported) {
        super.moveAbsolute(position, yaw, 0, yaw, isOnGround, teleported);
    }

    @Override
    public void moveRelative(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        super.moveRelative(relX, relY, relZ, yaw, 0, yaw, isOnGround);
    }

    @Override
    public void updatePositionAndRotation(double moveX, double moveY, double moveZ, float yaw, float pitch, boolean isOnGround) {
        moveRelative(moveX, moveY, moveZ, yaw + 90, pitch, isOnGround);
    }

    @Override
    public void updateRotation(float yaw, float pitch, boolean isOnGround) {
        moveRelative(0, 0, 0, yaw + 90, 0, 0, isOnGround);
    }

    public void setVariant(IntEntityMetadata entityMetadata) {
        variant = entityMetadata.getPrimitiveValue();
        dirtyMetadata.put(EntityDataTypes.VARIANT, switch (variant) {
            case 6, 7, 8 -> variant - 1; // dark_oak, mangrove, bamboo
            case 5 -> 8; // cherry
            default -> variant;
        });
    }

    public void setPaddlingLeft(BooleanEntityMetadata entityMetadata) {
        isPaddlingLeft = entityMetadata.getPrimitiveValue();
        if (isPaddlingLeft) {
            // Java sends simply "true" and "false" (is_paddling_left), Bedrock keeps sending packets as you're rowing
            // This is an asynchronous method that emulates Bedrock rowing until "false" is sent.
            paddleTimeLeft = 0f;
            if (!this.passengers.isEmpty()) {
                // Get the entity by the first stored passenger and convey motion in this manner
                Entity entity = this.passengers.get(0);
                if (entity != null) {
                    updateLeftPaddle(session, entity);
                }
            }
        } else {
            // Indicate that the row position should be reset
            dirtyMetadata.put(EntityDataTypes.ROW_TIME_LEFT, 0.0f);
        }
    }

    public void setPaddlingRight(BooleanEntityMetadata entityMetadata) {
        isPaddlingRight = entityMetadata.getPrimitiveValue();
        if (isPaddlingRight) {
            paddleTimeRight = 0f;
            if (!this.passengers.isEmpty()) {
                Entity entity = this.passengers.get(0);
                if (entity != null) {
                    updateRightPaddle(session, entity);
                }
            }
        } else {
            dirtyMetadata.put(EntityDataTypes.ROW_TIME_RIGHT, 0.0f);
        }
    }

    @Override
    protected InteractiveTag testInteraction(Hand hand) {
        if (session.isSneaking()) {
            return InteractiveTag.NONE;
        } else if (passengers.size() < 2) {
            return InteractiveTag.BOARD_BOAT;
        } else {
            return InteractiveTag.NONE;
        }
    }

    @Override
    public InteractionResult interact(Hand hand) {
        if (session.isSneaking()) {
            return InteractionResult.PASS;
        } else {
            // TODO: the client also checks for "out of control" ticks
            return InteractionResult.SUCCESS;
        }
    }

    private void updateLeftPaddle(GeyserSession session, Entity rower) {
        if (isPaddlingLeft) {
            paddleTimeLeft += ROWING_SPEED;
            sendAnimationPacket(session, rower, AnimatePacket.Action.ROW_LEFT, paddleTimeLeft);

            session.scheduleInEventLoop(() ->
                    updateLeftPaddle(session, rower),
                    100,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void updateRightPaddle(GeyserSession session, Entity rower) {
        if (isPaddlingRight) {
            paddleTimeRight += ROWING_SPEED;
            sendAnimationPacket(session, rower, AnimatePacket.Action.ROW_RIGHT, paddleTimeRight);

            session.scheduleInEventLoop(() ->
                            updateRightPaddle(session, rower),
                    100,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void sendAnimationPacket(GeyserSession session, Entity rower, AnimatePacket.Action action, float rowTime) {
        AnimatePacket packet = new AnimatePacket();
        packet.setRuntimeEntityId(rower.getGeyserId());
        packet.setAction(action);
        packet.setRowingTime(rowTime);
        session.sendUpstreamPacket(packet);
    }
}
